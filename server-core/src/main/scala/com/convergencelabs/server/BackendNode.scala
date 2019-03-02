package com.convergencelabs.server

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import com.convergencelabs.server.datastore.convergence.AuthenticationActor
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainStoreActor
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor
import com.convergencelabs.server.datastore.convergence.RoleStoreActor
import com.convergencelabs.server.datastore.convergence.UserFavoriteDomainStoreActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.db.provision.DomainProvisioner
import com.convergencelabs.server.db.provision.DomainProvisionerActor
import com.convergencelabs.server.db.schema.DatabaseManager
import com.convergencelabs.server.db.schema.DatabaseManagerActor
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.chat.ChatSharding
import com.convergencelabs.server.domain.model.ModelCreator
import com.convergencelabs.server.domain.model.ModelPermissionResolver
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.RestDomainActor
import com.convergencelabs.server.domain.rest.RestDomainActorSharding

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.sharding.ShardRegion
import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor
import com.convergencelabs.server.datastore.convergence.UserFavoriteDomainStore
import com.convergencelabs.server.datastore.convergence.ServerStatusActor

class BackendNode(system: ActorSystem, convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] var activityShardRegion: Option[ActorRef] = None
  private[this] var chatChannelRegion: Option[ActorRef] = None
  private[this] var domainReqion: Option[ActorRef] = None
  private[this] var realtimeModelRegion: Option[ActorRef] = None

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val dbServerConfig = system.settings.config.getConfig("convergence.persistence.server")
    val convergenceDbConfig = system.settings.config.getConfig("convergence.persistence.convergence-database")
    val domainPreRelease = system.settings.config.getBoolean("convergence.persistence.domain-databases.pre-release")

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    // TODO make this a config
    val shardCount = 100

    // Realtime Subsystem
    activityShardRegion =
      Some(ActivityActorSharding.start(system, shardCount))

    chatChannelRegion =
      Some(ChatSharding.start(system, shardCount))

    domainReqion =
      Some(DomainActorSharding.start(
        system,
        shardCount,
        List(
          protocolConfig,
          DomainPersistenceManagerActor,
          FiniteDuration(10, TimeUnit.SECONDS))))

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
    val databaseManagerActor = system.actorOf(DatabaseManagerActor.props(databaseManager), DatabaseManagerActor.RelativePath)

    val domainStoreActor = system.actorOf(DomainStoreActor.props(convergenceDbProvider, provisionerActor), DomainStoreActor.RelativePath)
    val importerActor = system.actorOf(ConvergenceImporterActor.props(
      dbServerConfig.getString("uri"),
      convergenceDbProvider,
      domainStoreActor), ConvergenceImporterActor.RelativePath)

    // Administrative actors
    val userManagerActor = system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor))
    val namespaceStoreActor = system.actorOf(NamespaceStoreActor.props(convergenceDbProvider), NamespaceStoreActor.RelativePath)
    val authStoreActor = system.actorOf(AuthenticationActor.props(convergenceDbProvider), AuthenticationActor.RelativePath)
    val convergenceUserActor = system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor), ConvergenceUserManagerActor.RelativePath)
    val roleStoreActor = system.actorOf(RoleStoreActor.props(convergenceDbProvider), RoleStoreActor.RelativePath)
    val configStoreActor = system.actorOf(ConfigStoreActor.props(convergenceDbProvider), ConfigStoreActor.RelativePath)
    val serverStatusActor = system.actorOf(ServerStatusActor.props(convergenceDbProvider), ServerStatusActor.RelativePath)
    val favoriteDomainStoreActor = system.actorOf(UserFavoriteDomainStoreActor.props(convergenceDbProvider), UserFavoriteDomainStoreActor.RelativePath)

    val domainRestSharding =
      Some(RestDomainActorSharding.start(system, shardCount, RestDomainActor.props(DomainPersistenceManagerActor, receiveTimeout)))

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {
    logger.info("Convergence Backend Node shutting down.")
    activityShardRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainReqion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
