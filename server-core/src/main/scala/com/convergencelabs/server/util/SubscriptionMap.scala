package com.convergencelabs.server.util

object SubscriptionMap {
  def apply[S,T](): SubscriptionMap[S, T] = {
    new SubscriptionMap()
  }
}

class SubscriptionMap[S, T] {

  var subscribersToTargets = Map[S, Set[T]]()
  var targetsToSubscribers = Map[T, Set[S]]()

  def subscribe(subscriber: S, target: T): Unit = {
    val targets = subscribersToTargets.getOrElse(subscriber, Set())
    subscribersToTargets += (subscriber -> (targets + target))

    val subscribers = targetsToSubscribers.getOrElse(target, Set())
    targetsToSubscribers += (target -> (subscribers + subscriber))
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
    subscribersToTargets.getOrElse(subscriber, Set())
  }

  def subscribers(target: T): Set[S] = {
    targetsToSubscribers.getOrElse(target, Set())
  }

  def removeTarget(target: T): Unit = {
    subscribers(target).foreach { subscriber =>
      unsubscribe(subscriber, target)
    }
  }
}
