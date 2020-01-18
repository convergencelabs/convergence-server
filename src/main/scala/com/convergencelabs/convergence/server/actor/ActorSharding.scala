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

package com.convergencelabs.convergence.server.actor

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}

/**
 * A utility base class that contains all of the needed functions to produce
 * and consume a Sharded Akka Actor.
 *
 * @param shardName  The name of the shard region in the Akka Cluster.
 * @param systemRole The Actor System Cluster Role on which to start the shard
 *                   regions.
 * @param actorClass The class of the Akka Actor that will be sharded.
 */
abstract class ActorSharding(val shardName: String,
                             val systemRole: String,
                             val actorClass: Class[_]) {

  /**
   * Starts the shard region for the given Actor assuming a the actor has a
   * zero argument constructor.
   *
   * @param system         The Actor System to start the shard in.
   * @param numberOfShards The number of shards to create across the cluster.
   * @return An ActorRef that allows consumers to send messages to the shard region.
   */
  def start(system: ActorSystem, numberOfShards: Int): ActorRef = {
    this.start(system, numberOfShards, List())
  }

  /**
   * Starts the shard region for the given Actor.
   *
   * @param system         The Actor System to start the shard in.
   * @param numberOfShards The number of shards to create across the cluster.
   * @param args           The arguments to pass to the Actors constructor.
   * @return An ActorRef that allows consumers to send messages to the shard region.
   */
  def start(system: ActorSystem, numberOfShards: Int, args: List[Any]): ActorRef = {
    val settings = ClusterShardingSettings
      .create(system)
      .withRole(this.systemRole)

    val sharedRegion = ClusterSharding.get(system).start(
      this.shardName,
      Props(this.actorClass, args: _*),
      settings,
      this.extractEntityId,
      this.extractShardId(numberOfShards))

    sharedRegion
  }

  /**
   * Starts a Shard Region proxy on a cluster node that doesn't host the
   * actual sharded actors.
   *
   * @param system         The ActorSystem to create the proxy in.
   * @param numberOfShards The number of shards the shard region was created with.
   * @return An ActorRef that allows consumers to send messages to the shard region.
   */
  def startProxy(system: ActorSystem, numberOfShards: Int): ActorRef = {
    ClusterSharding.get(system).startProxy(
      this.shardName,
      Some(this.systemRole),
      this.extractEntityId,
      this.extractShardId(numberOfShards))
  }

  /**
   * Gets a reference to the shard region, assuming it, or it's proxy, has
   * already been locally started.
   *
   * @return An ActorRef that allows consumers to send messages to the shard region.
   */
  def shardRegion(system: ActorSystem): ActorRef = {
    ClusterSharding.get(system).shardRegion(this.shardName)
  }

  /**
   * Extracts the entity id from a message sent to the Shard Region.
   *
   * @return The entity id, of the Actor the message should be sent to.
   */
  protected def extractEntityId: ShardRegion.ExtractEntityId

  /**
   * Extracts the Shard id of a given message, so it can be routed to the
   * correct shard.
   *
   * @param numberOfShards The number of shards the shard region was created with.
   * @return The shard Id of the shard the message should be sent to.
   */
  protected def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId
}
