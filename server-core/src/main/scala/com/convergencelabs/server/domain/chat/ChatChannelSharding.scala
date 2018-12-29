package com.convergencelabs.server.domain.chat

import com.convergencelabs.server.actor.ActorSharding
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage

import akka.cluster.sharding.ShardRegion

object ChatChannelSharding extends ActorSharding(
    "ChatChannelShardRegion",
    "backend",
    classOf[ChatChannelActor]){
  
  override val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ExistingChannelMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}::${msg.channelId}", msg)
  }
 
  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: ExistingChannelMessage => 
      Math.abs(msg.domainFqn.domainId.hashCode + msg.domainFqn.namespace.hashCode + msg.channelId.hashCode % numberOfShards).toString
  }
}
