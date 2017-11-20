package com.convergencelabs.server.domain

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion

object DomainActorSharding {
  val RegionName = "DomainActorShardRegion"
  def shardRegion(system: ActorSystem) = ClusterSharding(system).shardRegion(RegionName)
  def start(system: ActorSystem, shards: Int, props: Props): ActorRef = {
    val config = new DomainActorSharding(shards)
    ClusterSharding(system).start(
      typeName = DomainActorSharding.RegionName,
      entityProps = props,
      settings = ClusterShardingSettings(system),
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
  
  def startProxy(system: ActorSystem, shards: Int): ActorRef = {
    val config = new DomainActorSharding(shards)
    ClusterSharding(system).startProxy(
      typeName = DomainActorSharding.RegionName,
      role = None,
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
}

class DomainActorSharding(val numberOfShards: Int = 100) {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: DomainMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}", msg)
  }
 
  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: DomainMessage => 
      Math.abs(msg.domainFqn.hashCode % numberOfShards).toString
  }
}
