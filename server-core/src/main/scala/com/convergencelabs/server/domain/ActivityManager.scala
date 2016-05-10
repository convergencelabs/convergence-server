package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey
import akka.actor.ActorRef
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionJoined
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionLeft
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityOpenSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityCloseSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeaveSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateSet
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateCleared

private[domain] class ActivityManager {
  private[this] var openActivities = Map[String, Activity]()

  def open(activityId: String, sk: SessionKey, client: ActorRef, replyTo: ActorRef): Unit = {
    if (!openActivities.contains(activityId)) {
      // not yet open
      openActivities += (activityId -> new Activity(activityId))
    }

    val activity = openActivities(activityId)
    activity.open(sk, client, replyTo)
  }

  def close(activityId: String, sk: SessionKey, replyTo: ActorRef): Unit = {
    openActivities.get(activityId) match {
      case Some(activity) =>
        activity.close(sk, replyTo)
        if (activity.isEmpty) {
          this.openActivities -= activityId
        }
      case None =>
        replyTo ! new akka.actor.Status.Failure(new IllegalStateException("Must have activity open to join"))
    }
  }

  def join(activityId: String, sk: SessionKey, replyTo: ActorRef): Unit = {
    val activity = openActivities(activityId)
    activity.join(sk, replyTo)
  }

  def leave(activityId: String, sk: SessionKey, replyTo: ActorRef): Unit = {
    val activity = openActivities(activityId)
    activity.leave(sk, replyTo)
  }

  def setState(activityId: String, sk: SessionKey, key: String, value: Any): Unit = {
    val activity = openActivities(activityId)
    activity.setState(sk, key, value)
  }

  def clearState(activityId: String, sk: SessionKey, key: String): Unit = {
    val activity = openActivities(activityId)
    activity.clearState(sk, key)
  }
}

private class Activity(private[this] val activityId: String) {
  private[this] var openedSessions = Map[SessionKey, ActorRef]()
  private[this] var joinedSessions = Map[SessionKey, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  def isEmpty(): Boolean = {
    openedSessions.isEmpty
  }

  def getOpenedSessions(): Map[SessionKey, ActorRef] = {
    this.openedSessions
  }

  def isOpen(sk: SessionKey): Boolean = {
    this.openedSessions.contains(sk)
  }

  def open(sk: SessionKey, client: ActorRef, replyTo: ActorRef): Unit = {
    this.openedSessions.get(sk) match {
      case Some(x) =>
        throw new IllegalStateException("Session already opened")
      case None =>
        this.openedSessions += (sk -> client)
        replyTo ! ActivityOpenSuccess(getState())
    }
  }

  def close(sk: SessionKey, replyTo: ActorRef): Unit = {
    if (!isOpen(sk)) {
      throw throw new IllegalStateException("Session must have activity open to close it.")
    }

    if (isJoined(sk)) {
      leaveHelper(sk)
    }

    this.openedSessions -= sk
    replyTo ! ActivityCloseSuccess()
  }

  def getJoinedSessions(): Map[SessionKey, ActorRef] = {
    this.joinedSessions
  }

  def isJoined(sk: SessionKey): Boolean = {
    this.joinedSessions.contains(sk)
  }

  def join(sk: SessionKey, replyTo: ActorRef): Unit = {
    if (!isOpen(sk)) {
      throw throw new IllegalStateException("Session must have activity open to join.")
    }

    this.joinedSessions.get(sk) match {
      case Some(x) =>
        throw new IllegalStateException("Session already joined")
      case None =>

        val joiner = this.openedSessions(sk)
        val message = ActivitySessionJoined(activityId, sk)
        openedSessions.values filter (_ != joiner) foreach (_ ! message)

        this.joinedSessions += (sk -> this.openedSessions(sk))
        this.stateMap.join(sk)

        replyTo ! ActivityJoinSuccess()
    }
  }

  def leave(sk: SessionKey, replyTo: ActorRef): Unit = {
    if (!isJoined(sk)) {
      throw throw new IllegalStateException("Session be joined to activity in order to leave.")
    } else {
      leaveHelper(sk)
      replyTo ! ActivityLeaveSuccess()
    }
  }

  def leaveHelper(sk: SessionKey): Unit = {
    val leaver = this.openedSessions(sk)
    val message = ActivitySessionLeft(activityId, sk)
    openedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sk)
    this.joinedSessions -= sk
  }

  def setState(sk: SessionKey, key: String, value: Any): Unit = {
    if (isJoined(sk)) {
      stateMap.setState(sk, key, value)
      val setter = this.openedSessions(sk)
      val message = ActivityRemoteStateSet(activityId, sk, key, value)
      openedSessions.values.filter(_ != setter) foreach (_ ! message)
    }
  }

  def clearState(sk: SessionKey, key: String): Unit = {
    if (isJoined(sk)) {
      stateMap.clearState(sk, key)
      val clearer = this.openedSessions(sk)
      val message = ActivityRemoteStateCleared(activityId, sk, key)
      openedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  def getState(): Map[SessionKey, Map[String, Any]] = {
    stateMap.getState()
  }
}

class ActivityStateMap {
  private[this] var state = Map[SessionKey, Map[String, Any]]()

  def setState(sk: SessionKey, key: String, value: Any): Unit = {
    val sessionState = state(sk)
    state += (sk -> (sessionState + (key -> value)))
  }

  def clearState(sk: SessionKey, key: String): Unit = {
    val sessionState = state(sk)
    state += (sk -> (sessionState - key))
  }

  def getState(): Map[SessionKey, Map[String, Any]] = {
    state
  }

  def join(sk: SessionKey): Unit = {
    state += (sk -> Map[String, Any]())
  }

  def leave(sk: SessionKey): Unit = {
    state -= sk
  }
}
