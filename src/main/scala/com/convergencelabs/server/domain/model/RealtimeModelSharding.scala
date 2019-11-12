/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model

import com.convergencelabs.server.actor.ActorSharding

import akka.cluster.sharding.ShardRegion

object RealtimeModelSharding extends ActorSharding(
    "RealtimeModelShard",
    "backend",
    classOf[RealtimeModelActor]) {
  
  override val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: ModelMessage ⇒
      (s"${msg.domainId.namespace}::${msg.domainId.domainId}::${msg.modelId}", msg)
  }

  override def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case msg: ModelMessage =>
      Math.abs(msg.domainId.hashCode + msg.modelId.hashCode % numberOfShards).toString
  }
}
