/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.rest

import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.sharding.ShardRegion

object RestDomainActorSharding {
  val RegionName = "RestDomainActorSharding"
  val ClusterRoleName = "backend"
  
  def shardRegion(system: ActorSystem) = ClusterSharding(system).shardRegion(RegionName)
  def start(system: ActorSystem, shards: Int, props: Props): ActorRef = {
    val config = new RestDomainActorSharding(shards)
    ClusterSharding(system).start(
      typeName = RestDomainActorSharding.RegionName,
      entityProps = props,
      settings = ClusterShardingSettings(system).withRole(ClusterRoleName),
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
  
  def startProxy(system: ActorSystem, shards: Int): ActorRef = {
    val config = new RestDomainActorSharding(shards)
    ClusterSharding(system).startProxy(
      typeName = RestDomainActorSharding.RegionName,
      role = Some(ClusterRoleName),
      extractEntityId = config.extractEntityId,
      extractShardId = config.extractShardId)
  }
}

class RestDomainActorSharding(val numberOfShards: Int = 100) {

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: DomainRestMessage ⇒ 
      (s"${msg.domainFqn.namespace}::${msg.domainFqn.domainId}", msg)
  }
 
  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: DomainRestMessage => 
      Math.abs(msg.domainFqn.hashCode % numberOfShards).toString
  }
}
