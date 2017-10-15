package com.convergencelabs.server.domain.model.cluster

import akka.cluster.sharding.ShardRegion

class RealTimeModelSharding(val numberOfShards: Int = 100) {
  def calculateRegionName(modelId: String): String = {
    s"RealTimeModel-${modelId}"
  }
  
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: RealTimeModelMessage ⇒ 
      (msg.modelId, msg)
  }
 
  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: RealTimeModelMessage => 
      Math.abs(msg.modelId.hashCode % numberOfShards).toString
  }
}
