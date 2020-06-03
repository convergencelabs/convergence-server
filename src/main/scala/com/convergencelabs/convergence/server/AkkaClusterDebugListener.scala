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

package com.convergencelabs.convergence.server

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import akka.cluster.ClusterEvent.{MemberEvent, MemberRemoved, MemberUp}
import akka.cluster.typed.{Cluster, Subscribe, Unsubscribe}
import grizzled.slf4j.Logging

/**
 * A helper Actor that will listen to Akka Cluster events and log debug
 * messages to the console.
 */
object AkkaClusterDebugListener extends Logging {
  def apply(cluster: Cluster): Behavior[MemberEvent] =
    Behaviors.setup { context =>
      cluster.subscriptions ! Subscribe(context.self, classOf[MemberEvent])
      Behaviors.receiveMessage[MemberEvent] {
        case MemberUp(member) =>
          debug(s"Akka Cluster Member with role '${member.roles}' is Up: ${member.address}")
          Behaviors.same
        case MemberRemoved(member, previousStatus) =>
          debug(s"Akka Cluster Member is Removed: ${member.address} after $previousStatus")
          Behaviors.same
        case msg: MemberEvent =>
          debug(msg.toString)
          Behaviors.same
      }.receiveSignal {
        case (context, PostStop) =>
          cluster.subscriptions ! Unsubscribe(context.self)
          Behaviors.same
      }
    }
}
