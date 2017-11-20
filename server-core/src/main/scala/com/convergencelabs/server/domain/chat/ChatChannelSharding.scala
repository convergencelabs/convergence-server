package com.convergencelabs.server.domain.chat

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage

import akka.cluster.sharding.ShardRegion
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ClusterShardingSettings
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChatChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage

object ChatChannelSharding {
  val RegionName = "ChatChannelShardRegion"
  def shardRegion(system: ActorSystem) = ClusterSharding(system).shardRegion(RegionName)
  
  def start(system: ActorSystem, shards: Int, props: Props): ActorRef = {
    val config = new ChatChannelSharding(shards)
    ClusterSharding(system).start(
      typeName = ChatChannelSharding.RegionName,
      entityProps = props,
      settings = ClusterShardingSettings(system),
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
}

// TODO make the number of shards configurable in the application config.
class ChatChannelSharding(val numberOfShards: Int = 100) {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ExistingChannelMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}::${msg.channelId}", msg)
  }
 
  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: ExistingChannelMessage => 
      Math.abs(msg.domainFqn.domainId.hashCode + msg.domainFqn.namespace.hashCode + msg.channelId.hashCode % numberOfShards).toString
  }
  
}
