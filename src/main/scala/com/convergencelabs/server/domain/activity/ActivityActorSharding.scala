/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.activity

import com.convergencelabs.server.actor.ActorSharding

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
