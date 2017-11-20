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

class BackendNode(system: ActorSystem, dbProvider: DatabaseProvider) extends Logging {

  private[this] var chatChannelRegion: Option[ActorRef] = None
  private[this] var domainReqion: Option[ActorRef] = None
  private[this] var realtimeModelRegion: Option[ActorRef] = None

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val dbConfig = system.settings.config.getConfig("convergence.convergence-database")
    val domainStore = new DomainStore(dbProvider)
    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)
    val shardCount = 100
    
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

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {
    logger.info("Backend Node shutting down.")
    chatChannelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
    domainReqion.foreach(_ ! ShardRegion.GracefulShutdown)
    realtimeModelRegion.foreach(_ ! ShardRegion.GracefulShutdown)
  }
}
