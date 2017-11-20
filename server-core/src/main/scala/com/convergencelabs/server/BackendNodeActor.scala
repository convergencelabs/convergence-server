package com.convergencelabs.server

import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.Cluster
import java.util.concurrent.TimeUnit
import com.convergencelabs.server.domain.model.ModelPermissionResolver
import com.convergencelabs.server.domain.model.ModelCreator
import com.convergencelabs.server.domain.model.RealtimeModelActor
import scala.concurrent.duration.FiniteDuration
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.chat.ChatChannelSharding
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.chat.ChatChannelActor
import akka.cluster.sharding.ShardRegion
import akka.actor.actorRef2Scala
import com.convergencelabs.server.domain.DomainActor
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.chat.ChatChannelActor

object BackendNodeActor {
  val RelativeActorPath = "backendNodeActor"

  def props(
    domainStore: DomainStore,
    protocolConfig: ProtocolConfiguration,
    persistenceManager: DomainPersistenceManager): Props = Props(
    new BackendNodeActor(
      domainStore,
      protocolConfig,
      persistenceManager))
}

class BackendNodeActor(
  private[this] val domainStore: DomainStore,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val persistenceManager: DomainPersistenceManager)
    extends Actor with ActorLogging {

  log.debug("DomainManagerActor starting up with address: " + self.path)

  private[this] val cluster = Cluster(context.system)
  private[this] implicit val ec = context.dispatcher

  // TODO make this configurable
  val shardCount = 100
  
  private[this] val chatChannelRegion: ActorRef = 
    ChatChannelSharding.start(context.system, shardCount, Props(classOf[ChatChannelActor]))

  private[this] val domainReqion: ActorRef =
    DomainActorSharding.start(context.system, shardCount, DomainActor.props(
      protocolConfig, persistenceManager, FiniteDuration(10, TimeUnit.SECONDS)))

  val clientDataResponseTimeout = FiniteDuration(10, TimeUnit.SECONDS)
  val receiveTimeout = FiniteDuration(10, TimeUnit.SECONDS)
  private[this] val realtimeModelSharding: ActorRef =
    RealtimeModelSharding.start(context.system, shardCount, RealtimeModelActor.props(
      new ModelPermissionResolver(),
      new ModelCreator(),
      DomainPersistenceManagerActor,
      clientDataResponseTimeout,
      receiveTimeout))

  log.debug("DomainManager started.")

  def receive: Receive = {
    case message: Any => unhandled(message)
  }

  override def postStop(): Unit = {
    log.debug("DomainManager shutdown.")
    chatChannelRegion ! ShardRegion.GracefulShutdown
    domainReqion ! ShardRegion.GracefulShutdown
    realtimeModelSharding ! ShardRegion.GracefulShutdown
  }
}
