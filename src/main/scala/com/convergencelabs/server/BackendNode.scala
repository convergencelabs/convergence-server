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

class BackendNode(system: ActorSystem, convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] var activityShardRegion: Option[ActorRef] = None
  private[this] var chatChannelRegion: Option[ActorRef] = None
  private[this] var domainRegion: Option[ActorRef] = None
  private[this] var realtimeModelRegion: Option[ActorRef] = None

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val dbServerConfig = system.settings.config.getConfig("convergence.persistence.server")
    val convergenceDbConfig = system.settings.config.getConfig("convergence.persistence.convergence-database")
//    val domainPreRelease = system.settings.config.getBoolean("convergence.persistence.domain-databases.pre-release")

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    // TODO make this a config
    val shardCount = 100

    // Realtime Subsystem
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

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = UserSessionTokenReaperActor.props(convergenceDbProvider),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole("backend")),
      name = "UserSessionTokenReaper")

    val clientDataResponseTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    val receiveTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    realtimeModelRegion =
      Some(RealtimeModelSharding.start(system, shardCount, List(
        new ModelPermissionResolver(),
        new ModelCreator(),
        DomainPersistenceManagerActor,
        clientDataResponseTimeout,
        receiveTimeout)))

    // Rest Subsystem

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

    // Administrative actors
    system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor))
    system.actorOf(NamespaceStoreActor.props(convergenceDbProvider), NamespaceStoreActor.RelativePath)
    system.actorOf(AuthenticationActor.props(convergenceDbProvider), AuthenticationActor.RelativePath)
    system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor), ConvergenceUserManagerActor.RelativePath)
    system.actorOf(RoleStoreActor.props(convergenceDbProvider), RoleStoreActor.RelativePath)
    system.actorOf(UserApiKeyStoreActor.props(convergenceDbProvider), UserApiKeyStoreActor.RelativePath)
    system.actorOf(ConfigStoreActor.props(convergenceDbProvider), ConfigStoreActor.RelativePath)
    system.actorOf(ServerStatusActor.props(convergenceDbProvider), ServerStatusActor.RelativePath)
    system.actorOf(UserFavoriteDomainStoreActor.props(convergenceDbProvider), UserFavoriteDomainStoreActor.RelativePath)

    RestDomainActorSharding.start(system, shardCount, RestDomainActor.props(DomainPersistenceManagerActor, receiveTimeout))

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {
    logger.info("Convergence Backend Node shutting down.")
    activityShardRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
