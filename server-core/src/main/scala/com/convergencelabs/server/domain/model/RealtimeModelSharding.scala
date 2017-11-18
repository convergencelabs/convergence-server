package com.convergencelabs.server.domain.model

import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ClusterSharding
import akka.actor.ActorSystem

object RealtimeModelSharding {
  val RegionName = "RealtimeModelShard"
  
  def shardRegion(system: ActorSystem) = ClusterSharding(system).shardRegion(RegionName)
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
