package com.convergencelabs.server.domain.model

import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ClusterSharding
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ClusterShardingSettings
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

object RealtimeModelSharding {
  val RegionName = "RealtimeModelShard"
  def shardRegion(system: ActorSystem) = ClusterSharding(system).shardRegion(RegionName)
  def start(system: ActorSystem, shards: Int, props: Props): ActorRef = {
    val config = new RealtimeModelSharding(shards)
    ClusterSharding(system).start(
      typeName = RealtimeModelSharding.RegionName,
      entityProps = props,
      settings = ClusterShardingSettings(system),
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
  def startProxy(system: ActorSystem, shards: Int): ActorRef = {
    val config = new RealtimeModelSharding(shards)
    ClusterSharding(system).startProxy(
      typeName = RealtimeModelSharding.RegionName,
      role = None,
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
}

class RealtimeModelSharding(val numberOfShards: Int = 100) {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: RealTimeModelMessage ⇒
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}::${msg.modelId}", msg)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: RealTimeModelMessage =>
      Math.abs(msg.domainFqn.hashCode + msg.modelId.hashCode % numberOfShards).toString
  }
}
