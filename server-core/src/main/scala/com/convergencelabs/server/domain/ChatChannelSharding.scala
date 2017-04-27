package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.ChatChannelMessages.ChatChannelMessage

import akka.cluster.sharding.ShardRegion

object ChatChannelSharding {
  def calculateRegionName(domainFqn: DomainFqn): String = {
    s"ChatChannelRegion-${domainFqn.namespace}:${domainFqn.domainId}"
  }
  
  // TODO make a config
  val numberOfShards = 100
  
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ChatChannelMessage ⇒ 
      (msg.channelId, msg)
  }
 
  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: ChatChannelMessage => 
      Math.abs(msg.channelId.hashCode % numberOfShards).toString
  }
}
