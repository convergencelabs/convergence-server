package com.convergencelabs.server.domain

import com.convergencelabs.server.actor.ActorSharding

import akka.cluster.sharding.ShardRegion

object DomainActorSharding extends ActorSharding(
    "DomainActorShardRegion",
    "backend",
    classOf[DomainActor]){
  
  override val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: DomainMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}", msg)
  }
 
  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: DomainMessage => 
      Math.abs(msg.domainFqn.hashCode % numberOfShards).toString
  }
}
