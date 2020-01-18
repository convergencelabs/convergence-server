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

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.sharding.ShardRegion
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor
import com.convergencelabs.convergence.server.db.provision.{DomainProvisioner, DomainProvisionerActor}
import com.convergencelabs.convergence.server.db.schema.{DatabaseManager, DatabaseManagerActor}
import com.convergencelabs.convergence.server.domain.DomainActorSharding
import com.convergencelabs.convergence.server.domain.activity.ActivityActorSharding
import com.convergencelabs.convergence.server.domain.chat.ChatSharding
import com.convergencelabs.convergence.server.domain.model.{ModelCreator, ModelPermissionResolver, RealtimeModelSharding}
import com.convergencelabs.convergence.server.domain.rest.RestDomainActorSharding
import grizzled.slf4j.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * The [[BackendServices]] class is the main entry point that bootstraps the
 * core business logic services in the Convergence Server. It is responsible
 * for start that various Akka Actors the comprise the major subsystems (
 * Chat, Presence, Models, etc.).
 *
 * @param system                The Akka ActorSystem to start Actors in.
 * @param convergenceDbProvider A [[com.convergencelabs.convergence.server.db.DatabaseProvider]] that is connected to the
 *                              main convergence database.
 */
class BackendServices(system: ActorSystem, convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] var activityShardRegion: Option[ActorRef] = None
  private[this] var chatChannelRegion: Option[ActorRef] = None
  private[this] var domainRegion: Option[ActorRef] = None
  private[this] var realtimeModelRegion: Option[ActorRef] = None

  /**
   * Starts the Backend Services. Largely this method will start up all
   * of the Actors required to provide e the core Convergence Server
   * services.
   */
  def start(): Unit = {
    logger.info("Convergence Backend Services starting up...")

    val dbServerConfig = system.settings.config.getConfig("convergence.persistence.server")
    val convergenceDbConfig = system.settings.config.getConfig("convergence.persistence.convergence-database")
    val shardCount = system.settings.config.getInt("convergence.shard-count")

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    //
    // Realtime Subsystem
    //

    // This is a cluster singleton that cleans up User Session Tokens after they have expired.
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = UserSessionTokenReaperActor.props(convergenceDbProvider),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole("backend")),
      name = "UserSessionTokenReaper")

    // The below actors are sharded since they provide services to domains
    // and could potentially have a large number of entities (e.g. activities,
    // models, etc.

    val clientDataResponseTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.client-data-timeout").toNanos)

    val receiveTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.passivation-timeout").toNanos)

    val resyncTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.resynchronization-timeout").toNanos)

    realtimeModelRegion = Some(RealtimeModelSharding.start(system, shardCount, List(
      new ModelPermissionResolver(),
      new ModelCreator(),
      DomainPersistenceManagerActor,
      clientDataResponseTimeout,
      receiveTimeout,
      resyncTimeout)))

    activityShardRegion =
      Some(ActivityActorSharding.start(system, shardCount))

    chatChannelRegion =
      Some(ChatSharding.start(system, shardCount))

    val domainPassivationTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.domain.passivation-timeout").toNanos)
    domainRegion =
      Some(DomainActorSharding.start(
        system,
        shardCount,
        List(
          protocolConfig,
          DomainPersistenceManagerActor,
          domainPassivationTimeout)))

    //
    // REST Services
    //

    // These are Actors that serve up basic low volume Convergence Services such as
    // CRUD for users, roles, authentication, etc. These actors are not sharded.

    // Import, export, and domain / database provisioning
    val domainProvisioner = new DomainProvisioner(convergenceDbProvider, system.settings.config)
    val provisionerActor = system.actorOf(DomainProvisionerActor.props(domainProvisioner), DomainProvisionerActor.RelativePath)

    val databaseManager = new DatabaseManager(dbServerConfig.getString("uri"), convergenceDbProvider, convergenceDbConfig)
    system.actorOf(DatabaseManagerActor.props(databaseManager), DatabaseManagerActor.RelativePath)

    val domainStoreActor = system.actorOf(DomainStoreActor.props(convergenceDbProvider, provisionerActor), DomainStoreActor.RelativePath)
    system.actorOf(ConvergenceImporterActor.props(
      dbServerConfig.getString("uri"),
      convergenceDbProvider,
      domainStoreActor), ConvergenceImporterActor.RelativePath)

    system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor))
    system.actorOf(NamespaceStoreActor.props(convergenceDbProvider), NamespaceStoreActor.RelativePath)
    system.actorOf(AuthenticationActor.props(convergenceDbProvider), AuthenticationActor.RelativePath)
    system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor), ConvergenceUserManagerActor.RelativePath)
    system.actorOf(RoleStoreActor.props(convergenceDbProvider), RoleStoreActor.RelativePath)
    system.actorOf(UserApiKeyStoreActor.props(convergenceDbProvider), UserApiKeyStoreActor.RelativePath)
    system.actorOf(ConfigStoreActor.props(convergenceDbProvider), ConfigStoreActor.RelativePath)
    system.actorOf(ServerStatusActor.props(convergenceDbProvider), ServerStatusActor.RelativePath)
    system.actorOf(UserFavoriteDomainStoreActor.props(convergenceDbProvider), UserFavoriteDomainStoreActor.RelativePath)

    // This bootstraps the subsystem that handles REST calls for domains.
    // Since the number of domains is unbounded, these actors are sharded.
    RestDomainActorSharding.start(system, shardCount, List(DomainPersistenceManagerActor, receiveTimeout))

    logger.info("Convergence Backend Services started up.")
  }

  /**
   * Stops the backend services. Note that this does not stop the
   * ActorSystem.
   */
  def stop(): Unit = {
    logger.info("Convergence Backend Services shutting down.")
    activityShardRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
