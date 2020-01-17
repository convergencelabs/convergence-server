/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat

import akka.cluster.sharding.ShardRegion
import com.convergencelabs.convergence.server.actor.ActorSharding
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ExistingChatMessage

/**
 * Configures the sharding of the [[ChatActor]].
 */
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
