package com.convergencelabs.server

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.sharding.ShardRegion
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.convergencelabs.server.datastore.convergence._
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.db.provision.{DomainProvisioner, DomainProvisionerActor}
import com.convergencelabs.server.db.schema.{DatabaseManager, DatabaseManagerActor}
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.chat.ChatSharding
import com.convergencelabs.server.domain.model.{ModelCreator, ModelPermissionResolver, RealtimeModelSharding}
import com.convergencelabs.server.domain.rest.{RestDomainActor, RestDomainActorSharding}
import grizzled.slf4j.Logging

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

/**
 * The [[BackendServices]] class is the main entry point that bootstraps the
 * core business logic services in the Convergence Server. It is responsible
 * for start that various Akka Actors the comprise the major subsystems (
 * Chat, Presence, Models, etc.).
 *
 * @param system                The Akka [[ActorSystem]] to start Actors in.
 * @param convergenceDbProvider A [[DatabaseProvider]] that is connected to the
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
    val clientDataResponseTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    val receiveTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    realtimeModelRegion = Some(RealtimeModelSharding.start(system, shardCount, List(
      new ModelPermissionResolver(),
      new ModelCreator(),
      DomainPersistenceManagerActor,
      clientDataResponseTimeout,
      receiveTimeout)))

    activityShardRegion =
      Some(ActivityActorSharding.start(system, shardCount))

    chatChannelRegion =
      Some(ChatSharding.start(system, shardCount))

    domainRegion =
      Some(DomainActorSharding.start(
        system,
        shardCount,
        List(
          protocolConfig,
          DomainPersistenceManagerActor,
          FiniteDuration(10, TimeUnit.SECONDS))))

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
    RestDomainActorSharding.start(system, shardCount, RestDomainActor.props(DomainPersistenceManagerActor, receiveTimeout))

    logger.info("Convergence Backend Services started up.")
  }

  /**
   * Stops the backend services. Note that this does not stop the
   * [[ActorSystem]].
   */
  def stop(): Unit = {
    logger.info("Convergence Backend Services shutting down.")
    activityShardRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
