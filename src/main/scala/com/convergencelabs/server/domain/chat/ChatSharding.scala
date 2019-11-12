/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.chat

import com.convergencelabs.server.actor.ActorSharding
import com.convergencelabs.server.domain.chat.ChatMessages.ExistingChatMessage

import akka.cluster.sharding.ShardRegion

object ChatSharding extends ActorSharding(
    "ChatChannelShardRegion",
    "backend",
    classOf[ChatActor]){
  
  override val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ExistingChatMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}::${msg.chatId}", msg)
  }
 
  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: ExistingChatMessage => 
      Math.abs((msg.domainFqn.domainId.hashCode + msg.domainFqn.namespace.hashCode + msg.chatId.hashCode) % numberOfShards).toString
  }
}
