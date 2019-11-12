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

package com.convergencelabs.convergence.server.domain.activity

import com.convergencelabs.convergence.server.actor.ActorSharding

import akka.actor.Props
import akka.cluster.sharding.ShardRegion


object ActivityActorSharding extends ActorSharding(
    "ActivityActorShardRegion",
    "backend",
    classOf[ActivityActor]){
  
  override def extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: IncomingActivityMessage ⇒ 
      val id = s"${msg.domain.namespace}::${msg.domain.namespace}::${msg.activityId}"
      (id, msg)
  }
 
  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: IncomingActivityMessage => 
      Math.abs(msg.domain.hashCode + msg.activityId.hashCode % numberOfShards).toString
  }
}
