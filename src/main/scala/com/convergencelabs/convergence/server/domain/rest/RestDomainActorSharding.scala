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

package com.convergencelabs.convergence.server.domain.rest

import com.convergencelabs.convergence.server.domain.rest.RestDomainActor.DomainRestMessage

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
