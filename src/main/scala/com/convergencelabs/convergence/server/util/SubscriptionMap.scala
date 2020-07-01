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

package com.convergencelabs.convergence.server.util

object SubscriptionMap {
  def apply[S,T](): SubscriptionMap[S, T] = {
    new SubscriptionMap()
  }
}

final class SubscriptionMap[S, T] {

  private[this] var subscribersToTargets = Map[S, Set[T]]()
  private[this]var targetsToSubscribers = Map[T, Set[S]]()

  def subscribe(subscriber: S, target: T): Unit = {
    val targets = subscribersToTargets.getOrElse(subscriber, Set())
    val newTargets = targets + target
    subscribersToTargets += (subscriber -> newTargets)

    val subscribers = targetsToSubscribers.getOrElse(target, Set())
    val newSubscribers = subscribers + subscriber
    targetsToSubscribers += (target -> newSubscribers)
  }

  def isSubscribed(subscriber: S, target: T): Boolean = {
    subscribersToTargets get (subscriber) map (_.contains(target)) getOrElse (false)
  }

  def unsubscribe(subscriber: S, target: T): Unit = {
    val targets = subscribersToTargets.getOrElse(subscriber, Set())
    val updatedTargets = (targets - target)
    if (updatedTargets.isEmpty) {
      subscribersToTargets -= subscriber
    } else {
      subscribersToTargets += (subscriber -> updatedTargets)
    }

    val subscribers = targetsToSubscribers.getOrElse(target, Set())
    val updatedSubscriber = (subscribers - subscriber)
    if (updatedSubscriber.isEmpty) {
      targetsToSubscribers -= target
    } else {
      targetsToSubscribers += (target -> updatedSubscriber)
    }
  }

  def unsubscribe(subscriber: S): Unit = {
    subscriptions(subscriber).foreach { target =>
      unsubscribe(subscriber, target)
    }
  }

  def subscriptions(subscriber: S): Set[T] = {
    val subscriptions = subscribersToTargets.getOrElse(subscriber, Set())
    subscriptions
  }

  def subscribers(target: T): Set[S] = {
    val subscribers = targetsToSubscribers.getOrElse(target, Set())
    subscribers
  }

  def removeTarget(target: T): Unit = {
    subscribers(target).foreach { subscriber =>
      unsubscribe(subscriber, target)
    }
  }
}
