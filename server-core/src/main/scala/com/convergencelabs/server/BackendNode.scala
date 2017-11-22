package com.convergencelabs.server

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainActor
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.chat.ChatChannelActor
import com.convergencelabs.server.domain.chat.ChatChannelSharding
import com.convergencelabs.server.domain.model.ModelCreator
import com.convergencelabs.server.domain.model.ModelPermissionResolver
import com.convergencelabs.server.domain.model.RealtimeModelActor
import com.convergencelabs.server.domain.model.RealtimeModelSharding

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ShardRegion
import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.db.provision.DomainProvisioner
import com.convergencelabs.server.db.provision.DomainProvisionerActor
import com.convergencelabs.server.datastore.DomainStoreActor
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.datastore.AuthStoreActor
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.RegistrationActor
import com.convergencelabs.server.domain.rest.AuthorizationActor
import com.convergencelabs.server.datastore.PermissionsStoreActor
import com.convergencelabs.server.db.schema.DatabaseManager
import com.convergencelabs.server.db.schema.DatabaseManagerActor
import com.convergencelabs.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.server.domain.rest.RestDomainActor

class BackendNode(system: ActorSystem, convergenceDbProvider: DatabaseProvider) extends Logging {

  private[this] var chatChannelRegion: Option[ActorRef] = None
  private[this] var domainReqion: Option[ActorRef] = None
  private[this] var realtimeModelRegion: Option[ActorRef] = None

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val convergenceDbConfig = system.settings.config.getConfig("convergence.convergence-database")
    val orientDbConfig = system.settings.config.getConfig("convergence.orient-db")
    val domainPreRelease = system.settings.config.getBoolean("convergence.domain-databases.pre-release")

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)
    
    // TODO make this a config
    val shardCount = 100

    // Realtime Stuff
    chatChannelRegion =
      Some(ChatChannelSharding.start(system, shardCount, Props(classOf[ChatChannelActor])))

    domainReqion =
      Some(DomainActorSharding.start(system, shardCount, DomainActor.props(
        protocolConfig, DomainPersistenceManagerActor, FiniteDuration(10, TimeUnit.SECONDS))))

    val clientDataResponseTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    val receiveTimeout = FiniteDuration(10, TimeUnit.SECONDS)
    realtimeModelRegion =
      Some(RealtimeModelSharding.start(system, shardCount, RealtimeModelActor.props(
        new ModelPermissionResolver(),
        new ModelCreator(),
        DomainPersistenceManagerActor,
        clientDataResponseTimeout,
        receiveTimeout)))

    // Rest Stuff

    // Import, export, and domain / database provisioning        
    val historyStore = new DeltaHistoryStore(convergenceDbProvider)
    val domainProvisioner = new DomainProvisioner(
      historyStore,
      orientDbConfig.getString("db-uri"),
      orientDbConfig.getString("admin-username"),
      orientDbConfig.getString("admin-password"),
      domainPreRelease)

    val provisionerActor = system.actorOf(DomainProvisionerActor.props(domainProvisioner), DomainProvisionerActor.RelativePath)

    val databaseManager = new DatabaseManager(orientDbConfig.getString("db-uri"), convergenceDbProvider, convergenceDbConfig)
    val databaseManagerActor = system.actorOf(DatabaseManagerActor.props(databaseManager), DatabaseManagerActor.RelativePath)

    val domainStoreActor = system.actorOf(DomainStoreActor.props(convergenceDbProvider, provisionerActor), DomainStoreActor.RelativePath)
     val importerActor = system.actorOf(ConvergenceImporterActor.props(
      orientDbConfig.getString("db-uri"),
      convergenceDbProvider,
      domainStoreActor), ConvergenceImporterActor.RelativePath)

    // Administrative actors
    val userManagerActor = system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor))
    val authStoreActor = system.actorOf(AuthStoreActor.props(convergenceDbProvider), AuthStoreActor.RelativePath)
    val registrationActor = system.actorOf(RegistrationActor.props(convergenceDbProvider, userManagerActor), RegistrationActor.RelativePath)
    val convergenceUserActor = system.actorOf(ConvergenceUserManagerActor.props(convergenceDbProvider, domainStoreActor), ConvergenceUserManagerActor.RelativePath)
    
    val authorizationActor = system.actorOf(AuthorizationActor.props(convergenceDbProvider), AuthorizationActor.RelativePath)
    val permissionStoreActor = system.actorOf(PermissionsStoreActor.props(convergenceDbProvider), PermissionsStoreActor.RelativePath)
    
    val domainRestSharding =
      Some(RestDomainActorSharding.start(system, shardCount, RestDomainActor.props(DomainPersistenceManagerActor, receiveTimeout)))
      
    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {
    logger.info("Backend Node shutting down.")
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainReqion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
