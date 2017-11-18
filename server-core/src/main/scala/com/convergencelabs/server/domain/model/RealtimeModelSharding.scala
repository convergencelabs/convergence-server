package com.convergencelabs.server.domain.model

import akka.cluster.sharding.ShardRegion

object RealtimeModelSharding {
  val RegionName = "RealtimeModelShard"
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
