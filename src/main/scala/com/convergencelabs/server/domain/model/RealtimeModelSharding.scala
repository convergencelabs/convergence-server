package com.convergencelabs.server.domain.model

import com.convergencelabs.server.actor.ActorSharding

import akka.cluster.sharding.ShardRegion

object RealtimeModelSharding extends ActorSharding(
    "RealtimeModelShard",
    "backend",
    classOf[RealtimeModelActor]) {
  
  override val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ModelMessage ⇒
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}::${msg.modelId}", msg)
  }

  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: ModelMessage =>
      Math.abs(msg.domainFqn.hashCode + msg.modelId.hashCode % numberOfShards).toString
  }
}
