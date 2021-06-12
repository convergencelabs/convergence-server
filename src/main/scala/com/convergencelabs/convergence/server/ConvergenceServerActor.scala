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
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.ConvergenceServerActor.Message
import com.convergencelabs.convergence.server.api.realtime.{ClientActorCreator, ConvergenceRealtimeApi, ProtocolConfiguration}
import com.convergencelabs.convergence.server.api.rest.ConvergenceRestApi
import com.convergencelabs.convergence.server.backend.BackendServices
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityActorSharding}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatActorSharding, ChatDeliveryActor, ChatDeliveryActorSharding, ChatServiceActor, ChatServiceActorSharding}
import com.convergencelabs.convergence.server.backend.services.domain.identity.{IdentityServiceActor, IdentityServiceActorSharding}
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationServiceActor, ModelOperationServiceActorSharding, ModelServiceActor, ModelServiceActorSharding, RealtimeModelActor, RealtimeModelSharding}
import com.convergencelabs.convergence.server.backend.services.domain.presence.{PresenceServiceActor, PresenceServiceActorSharding}
import com.convergencelabs.convergence.server.backend.services.domain.rest.{DomainRestActor, DomainRestActorSharding}
import com.convergencelabs.convergence.server.backend.services.domain.{DomainSessionActor, DomainSessionActorSharding}
import com.convergencelabs.convergence.server.backend.services.server.{ConvergenceDatabaseInitializerActor, DomainLifecycleTopic}
import com.typesafe.config.ConfigRenderOptions
import grizzled.slf4j.Logging

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

/**
 * TThe [[ConvergenceServerActor]] is the root Guardian actor of all
 * Convergence services. It is responsible for spawning all of the
 * actors that implement the system as well as for starting
 * the rest and realtime akka-http services.
 *
 * @param context ActorContext for this actor / behavior.
 */
private[server] final class ConvergenceServerActor(context: ActorContext[Message]) extends AbstractBehavior[Message](context) with Logging {

  import ConvergenceServerActor._
  import ConvergenceServerConstants._

  private[this] val config = context.system.settings.config

  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendServices] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None
  private[this] var clusterListener: Option[ActorRef[MemberEvent]] = None

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: StartRequest =>
        start(msg)
      case msg: StartBackendServices =>
        startBackend(msg)
      case msg: BackendInitializationFailure =>
        onBackendFailure(msg)
    }
  }

  /**
   * Starts the Convergence Server and returns itself, supporting
   * a fluent API.
   *
   * @return This instance of the ConvergenceServer
   */
  private[this] def start(msg: StartRequest): Behavior[Message] = {
    info(s"Convergence Server (${BuildInfo.version}) starting up...")

    debug(s"Rendering configuration: \n ${config.root().render(ConfigRenderOptions.concise())}")

    val cluster = Cluster(context.system)
    this.cluster = Some(cluster)
    this.clusterListener = Some(context.spawn(AkkaClusterDebugListener(cluster), "clusterListener"))

    val roles = config.getStringList(AkkaConfig.AkkaClusterRoles).asScala.toSet
    info(s"Convergence Server Roles: [${roles.mkString(", ")}]")

    val shardCount = context.system.settings.config.getInt("convergence.shard-count")

    val domainLifeCycleTopic = context.spawn(DomainLifecycleTopic.TopicBehavior, DomainLifecycleTopic.TopicName)

    val sharding = ClusterSharding(context.system)

    val realtimeModelShardRegion = RealtimeModelSharding(context.system.settings.config, sharding, shardCount)
    val activityShardRegion = ActivityActorSharding(context.system, sharding, shardCount)
    val chatDeliveryShardRegion = ChatDeliveryActorSharding(sharding, shardCount)
    val chatShardRegion = ChatActorSharding(sharding, shardCount, chatDeliveryShardRegion.narrow[ChatDeliveryActor.Send])
    val domainShardRegion = DomainSessionActorSharding(config, sharding, shardCount, () => {
      domainLifeCycleTopic
    })

    val modelServiceShardRegion = ModelServiceActorSharding(config, sharding, shardCount)
    val modelOperationServiceShardRegion = ModelOperationServiceActorSharding(config, sharding, shardCount)
    val identityServiceShardRegion = IdentityServiceActorSharding(config, sharding, shardCount)
    val presenceServiceShardRegion = PresenceServiceActorSharding(config, sharding, shardCount)
    val chatServiceShardRegion = ChatServiceActorSharding(config, sharding, shardCount)

    val domainRestShardRegion = DomainRestActorSharding(config, sharding, shardCount, modelServiceShardRegion, chatServiceShardRegion)

    val backendStartupFuture = if (roles.contains(ServerClusterRoles.Backend)) {
      this.processBackendRole(domainLifeCycleTopic)
    } else {
      Future.successful(())
    }

    val restStartupFuture = if (roles.contains(ServerClusterRoles.RestApi)) {
      this.processRestApiRole(domainRestShardRegion, realtimeModelShardRegion, chatShardRegion)
    } else {
      Future.successful(())
    }

    val realtimeStartupFuture = if (roles.contains(ServerClusterRoles.RealtimeApi)) {
      this.processRealtimeApiRole(
        domainShardRegion,
        modelServiceShardRegion,
        modelOperationServiceShardRegion,
        chatServiceShardRegion,
        identityServiceShardRegion,
        presenceServiceShardRegion,
        activityShardRegion,
        realtimeModelShardRegion,
        chatShardRegion,
        chatDeliveryShardRegion,
        domainLifeCycleTopic)
    } else {
      Future.successful(())
    }

    implicit val ec: ExecutionContext = ExecutionContext.global
    (for {
      _ <- backendStartupFuture
      _ <- restStartupFuture
      _ <- realtimeStartupFuture
    } yield {
      msg.replyTo ! StartResponse(Right(Ok()))
    }).recover { cause =>
      error("The was an error starting the ConvergenceServerActor", cause)
      msg.replyTo ! StartResponse(Left(()))
    }

    Behaviors.same
  }

  /**
   * A helper method used to start the backend services when they are ready to
   * be started.
   *
   * @param msg The message containing the request to start the backend services.
   * @return The next Behavior this actor should exhibit.
   */
  private[this] def startBackend(msg: StartBackendServices): Behavior[Message] = {
    val StartBackendServices(domainLifecycleTopic, promise) = msg

    val backend = new BackendServices(context, domainLifecycleTopic)
    backend.start()
      .map { _ =>
        this.backend = Some(backend)
        promise.success(())
      }
      .recover { cause =>
        promise.failure(cause)
      }

    Behaviors.same
  }

  /**
   * A help method to handle the case where backend initialization fails.
   *
   * @param msg The message indicating initialization failed.
   * @return The next behavior to exhibit.
   */
  private[this] def onBackendFailure(msg: BackendInitializationFailure): Behavior[Message] = {
    val BackendInitializationFailure(cause, p) = msg
    p.failure(cause)
    Behaviors.same
  }

  /**
   * A helper method that will bootstrap the backend node.
   */
  private[this] def processBackendRole(domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Future[Unit] = {
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

    val p = Promise[Unit]()

    context.ask(convergenceDatabaseInitializerActor, ConvergenceDatabaseInitializerActor.AssertInitialized) {
      case Success(ConvergenceDatabaseInitializerActor.Initialized()) =>
        StartBackendServices(domainLifecycleTopic, p)
      case Success(ConvergenceDatabaseInitializerActor.InitializationFailed(cause)) =>
        BackendInitializationFailure(cause, p)
      case Failure(cause) =>
        BackendInitializationFailure(cause, p)
    }

    p.future
  }

  /**
   * A helper method that will bootstrap the Rest API.
   */
  private[this] def processRestApiRole(domainRestRegion: ActorRef[DomainRestActor.Message],
                                       modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                                       chatClusterRegion: ActorRef[ChatActor.Message]): Future[Unit] = {
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
    this.rest = Some(restFrontEnd)
    restFrontEnd.start()
  }

  /**
   * A helper method that will bootstrap the Realtime Api.
   */
  private[this] def processRealtimeApiRole(domainRegion: ActorRef[DomainSessionActor.Message],
                                           modelService: ActorRef[ModelServiceActor.Message],
                                           modelOperationService: ActorRef[ModelOperationServiceActor.Message],
                                           chatService: ActorRef[ChatServiceActor.Message],
                                           identityService: ActorRef[IdentityServiceActor.Message],
                                           presenceService: ActorRef[PresenceServiceActor.Message],
                                           activityShardRegion: ActorRef[ActivityActor.Message],
                                           modelShardRegion: ActorRef[RealtimeModelActor.Message],
                                           chatShardRegion: ActorRef[ChatActor.Message],
                                           chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                                           domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Future[Unit] = {

    info("Role 'realtimeApi' detected, activating the Realtime API...")
    val protoConfig = ProtocolConfiguration(context.system.settings.config)
    val clientCreator = context.spawn(ClientActorCreator(
      protoConfig,
      domainRegion,
      modelService,
      modelOperationService,
      chatService,
      identityService,
      presenceService,
      activityShardRegion,
      modelShardRegion,
      chatShardRegion,
      chatDeliveryShardRegion,
      domainLifecycleTopic),
      "ClientCreatorActor")

    val host = config.getString("convergence.realtime.host")
    val port = config.getInt("convergence.realtime.port")

    val realTimeFrontEnd = new ConvergenceRealtimeApi(context.system, clientCreator, host, port)
    this.realtime = Some(realTimeFrontEnd)
    realTimeFrontEnd.start()
  }
}

object ConvergenceServerActor {

  /**
   * Creates a ne ConvergenceServerActor behavior.
   *
   * @return The newly created Behavior.
   */
  def apply(): Behavior[Message] = Behaviors.setup(new ConvergenceServerActor(_))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  /**
   * The base trait for all messages that can be sent to the
   * ConvergenceServerActor.
   */
  sealed trait Message

  //
  // Start
  //

  /**
   * Requests that the Convergence Server Actor start and initialize all of
   * the configured server roles.
   *
   * @param replyTo The actor to reply to on success or failure of startup.
   */
  final case class StartRequest(replyTo: ActorRef[StartResponse]) extends Message

  /**
   * The message to send back to the actor that requested start up.
   *
   * @param response Right(OK()) if startup was successful, or Left(()) if
   *                 startup failed for some reason.
   */
  final case class StartResponse(response: Either[Unit, Ok])


  //////////////////////
  // Internal Messages
  //////////////////////

  /**
   * A internal message that indicates all required resources have been
   * acquired that are necessary to start the backend services.
   *
   * @param domainLifecycleTopic The distributed pub-sub topic for domain
   *                             lifecycle events.
   * @param startPromise         The promise used for starting up.
   */
  private final case class StartBackendServices(domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage],
                                                startPromise: Promise[Unit]) extends Message

  /**
   * Indicates that initializing the backend services has failed.
   *
   * @param cause        The exception that caused the failure.
   * @param startPromise The promise used for starting up.
   */
  private final case class BackendInitializationFailure(cause: Throwable,
                                                        startPromise: Promise[Unit]) extends Message

}
