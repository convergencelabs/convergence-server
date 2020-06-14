/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed._
import akka.util.Timeout
import com.convergencelabs.convergence.server.ConvergenceServerActor.Command
import com.convergencelabs.convergence.server.api.realtime.ConvergenceRealtimeApi
import com.convergencelabs.convergence.server.api.rest.ConvergenceRestApi
import com.convergencelabs.convergence.server.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.db.{ConvergenceDatabaseInitializerActor, PooledDatabaseProvider}
import com.convergencelabs.convergence.server.domain.{DomainActor, DomainActorSharding}
import com.convergencelabs.convergence.server.domain.activity.{ActivityActor, ActivityActorSharding}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatActorSharding, ChatDeliveryActor, ChatDeliveryActorSharding}
import com.convergencelabs.convergence.server.domain.model.{RealtimeModelActor, RealtimeModelSharding}
import com.convergencelabs.convergence.server.domain.rest.{DomainRestActor, DomainRestActorSharding}
import com.orientechnologies.orient.core.db.{OrientDB, OrientDBConfig}
import com.typesafe.config.ConfigRenderOptions
import grizzled.slf4j.Logging

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}


object ConvergenceServerActor {

  sealed trait Command

  case object Start extends Command

  case object Stop extends Command

  private case class StartBackendServices(domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]) extends Command

  private case class BackendInitializationFailure(cause: Throwable) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new ConvergenceServerActor(context))
}

/**
 * This is the main ConvergenceServer class. It is responsible for starting
 * up all services including the Akka Actor System.
 *
 * @param context The configuration to use for the server.
 */
class ConvergenceServerActor(private[this] val context: ActorContext[Command])
  extends AbstractBehavior[Command](context)
    with Logging {

  import ConvergenceServerActor._

  private[this] val config = context.system.settings.config

  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendServices] = None
  private[this] var orientDb: Option[OrientDB] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None
  private[this] var clusterListener: Option[ActorRef[MemberEvent]] = None

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Start =>
        start()
      case Stop =>
        stop()
      case msg: StartBackendServices =>
        startBackend(msg)
      case BackendInitializationFailure(cause) =>
        error("Failed to start backend services", cause)
        stop()
    }
  }

  private[this] def startBackend(msg: StartBackendServices): Behavior[Command] = {
    val StartBackendServices(domainLifecycleTopic) = msg
    val persistenceConfig = config.getConfig("convergence.persistence")
    val dbServerConfig = persistenceConfig.getConfig("server")

    val baseUri = dbServerConfig.getString("uri")
    orientDb = Some(new OrientDB(baseUri, OrientDBConfig.defaultConfig()))

    // TODO make the pool size configurable
    val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")
    val convergenceDatabase = convergenceDbConfig.getString("database")
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")

    val dbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password)
    dbProvider.connect().get

    val domainStore = new DomainStore(dbProvider)

    context.spawn(DomainPersistenceManagerActor(baseUri, domainStore, domainLifecycleTopic), "DomainPersistenceManager")

    val backend = new BackendServices(context, dbProvider, domainLifecycleTopic)
    backend.start()
    this.backend = Some(backend)
    Behaviors.same
  }

  /**
   * Starts the Convergence Server and returns itself, supporting
   * a fluent API.
   *
   * @return This instance of the ConvergenceServer
   */
  private[this] def start(): ConvergenceServerActor = {
    info(s"Convergence Server (${BuildInfo.version}) starting up...")

    debug(s"Rendering configuration: \n ${config.root().render(ConfigRenderOptions.concise())}")

    val cluster = Cluster(context.system)
    this.cluster = Some(cluster)
    this.clusterListener = Some(context.spawn(AkkaClusterDebugListener(cluster), "clusterListener"))

    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toSet
    info(s"Convergence Server Roles: ${roles.mkString(", ")}")


    val shardCount = context.system.settings.config.getInt("convergence.shard-count")

    val domainLifeCycleTopic = context.spawn(DomainLifecycleTopic.TopicBehavior, DomainLifecycleTopic.TopicName)

    val sharding = ClusterSharding(context.system)

    val modelShardRegion = RealtimeModelSharding(context.system.settings.config, sharding, shardCount)
    val activityShardRegion = ActivityActorSharding(context.system, sharding, shardCount)
    val chatDeliveryShardRegion = ChatDeliveryActorSharding(sharding, shardCount)
    val chatShardRegion = ChatActorSharding(sharding, shardCount, chatDeliveryShardRegion.narrow[ChatDeliveryActor.Send])
    val domainShardRegion = DomainActorSharding(context.system.settings.config, sharding, shardCount, () => {
      domainLifeCycleTopic
    })

    val domainRestShardRegion = DomainRestActorSharding(context.system.settings.config, sharding, shardCount)


    if (roles.contains(ServerClusterRoles.Backend)) {
      this.processBackendRole(domainLifeCycleTopic)
    }
    if (roles.contains(ServerClusterRoles.RestApi)) {
      this.processRestApiRole(domainRestShardRegion, modelShardRegion, chatShardRegion)
    }

    if (roles.contains(ServerClusterRoles.RealtimeApi)) {
      this.processRealtimeApiRole(
        domainShardRegion,
        activityShardRegion,
        modelShardRegion,
        chatShardRegion,
        chatDeliveryShardRegion,
        domainLifeCycleTopic)
    }

    this
  }

  /**
   * Stops the Convergence Server.
   */
  private[this] def stop(): Behavior[Command] = {
    logger.info(s"Stopping the Convergence Server...")

    clusterListener.foreach(context.stop(_))

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())
    this.orientDb.foreach(db => db.close())

    logger.info(s"Leaving the cluster")
    cluster.foreach(c => c.manager ! Leave(c.selfMember.address))

    Behaviors.stopped
  }

  /**
   * A helper method that will bootstrap the backend node.
   */
  private[this] def processBackendRole(domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Unit = {
    info("Role 'backend' detected, activating Backend Services...")

    val singletonManager = ClusterSingleton(context.system)
    val convergenceDatabaseInitializerActor = singletonManager.init(
      SingletonActor(Behaviors.supervise(ConvergenceDatabaseInitializerActor())
        .onFailure[Exception](SupervisorStrategy.restart), "ConvergenceDatabaseInitializer")
        .withSettings(ClusterSingletonSettings(context.system).withRole(ServerClusterRoles.Backend))
    )

    info("Ensuring convergence database is initialized")
    val initTimeout = config.getDuration("convergence.persistence.convergence-database.initialization-timeout")
    implicit val timeout: Timeout = Timeout.durationToTimeout(Duration.fromNanos(initTimeout.toNanos))

    context.ask(convergenceDatabaseInitializerActor, ConvergenceDatabaseInitializerActor.AssertInitialized) {
      case Success(ConvergenceDatabaseInitializerActor.Initialized()) =>
        StartBackendServices(domainLifecycleTopic)
      case Success(ConvergenceDatabaseInitializerActor.InitializationFailed(cause)) =>
        BackendInitializationFailure(cause)
      case Failure(cause) =>
        BackendInitializationFailure(cause)
    }
  }

  /**
   * A helper method that will bootstrap the Rest API.
   */
  private[this] def processRestApiRole(domainRestRegion: ActorRef[DomainRestActor.Message],
                                       modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                                       chatClusterRegion: ActorRef[ChatActor.Message]): Unit = {
    info("Role 'restApi' detected, activating REST API...")
    val host = config.getString("convergence.rest.host")
    val port = config.getInt("convergence.rest.port")
    val restFrontEnd = new ConvergenceRestApi(
      host,
      port,
      context,
      domainRestRegion,
      modelClusterRegion,
      chatClusterRegion
    )
    restFrontEnd.start()
    this.rest = Some(restFrontEnd)
  }

  /**
   * A helper method that will bootstrap the Realtime Api.
   */
  private[this] def processRealtimeApiRole(domainRegion: ActorRef[DomainActor.Message],
                                           activityShardRegion: ActorRef[ActivityActor.Message],
                                           modelShardRegion: ActorRef[RealtimeModelActor.Message],
                                           chatShardRegion: ActorRef[ChatActor.Message],
                                           chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                                           domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Unit = {

    info("Role 'realtimeApi' detected, activating the Realtime API...")
    val host = config.getString("convergence.realtime.host")
    val port = config.getInt("convergence.realtime.port")
    val realTimeFrontEnd = new ConvergenceRealtimeApi(
      context,
      host,
      port,
      domainRegion,
      activityShardRegion,
      modelShardRegion,
      chatShardRegion,
      chatDeliveryShardRegion,
      domainLifecycleTopic)
    realTimeFrontEnd.start()
    this.realtime = Some(realTimeFrontEnd)
  }
}


