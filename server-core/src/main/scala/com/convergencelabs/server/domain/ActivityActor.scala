package com.convergencelabs.server.domain

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionJoined
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionLeft
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityOpenSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityCloseSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeaveSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateSet
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateCleared
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityOpenRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityCloseRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeaveRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySetState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityClearState
import akka.actor.Status
import akka.actor.Terminated

object ActivityActor {
  def props(activityId: String): Props = Props(
    new ActivityActor(activityId))
}

private[domain] class ActivityActor(private[this] val activityId: String)
    extends Actor with ActorLogging {

  private[this] var openedSessions = Map[SessionKey, ActorRef]()
  private[this] var openedClients = Map[ActorRef, SessionKey]()
  private[this] var joinedSessions = Map[SessionKey, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  def receive: Receive = {
    case ActivityOpenRequest(id, sk, client) => open(sk, client)
    case ActivityCloseRequest(id, sk) => close(sk)
    case ActivityJoinRequest(id, sk) => join(sk)
    case ActivityLeaveRequest(id, sk) => leave(sk)
    case ActivitySetState(id, sk, key, value) => setState(sk, key, value)
    case ActivityClearState(id, sk, key) => clearState(sk, key)
    case Terminated(actor) => handleClientDeath(actor)
  }

  private[this] def isEmpty(): Boolean = {
    openedSessions.isEmpty
  }

  private[this] def getOpenedSessions(): Map[SessionKey, ActorRef] = {
    this.openedSessions
  }

  private[this] def isOpen(sk: SessionKey): Boolean = {
    this.openedSessions.contains(sk)
  }

  private[this] def open(sk: SessionKey, client: ActorRef): Unit = {
    this.openedSessions.get(sk) match {
      case Some(x) =>
        sender ! Status.Failure(new IllegalStateException("Session already has activity open."))
      case None =>
        this.openedSessions += (sk -> client)
        this.openedClients += (client -> sk)
        context.watch(client)
        sender ! ActivityOpenSuccess(stateMap.getState())
    }
  }

  private[this] def close(sk: SessionKey): Unit = {
    if (!isOpen(sk)) {
      throw throw new IllegalStateException("Session must have activity open to close it.")
    }

    if (isJoined(sk)) {
      leaveHelper(sk)
    }

    this.openedSessions -= sk
    sender ! ActivityCloseSuccess()
  }

  private[this] def getJoinedSessions(): Map[SessionKey, ActorRef] = {
    this.joinedSessions
  }

  private[this] def isJoined(sk: SessionKey): Boolean = {
    this.joinedSessions.contains(sk)
  }

  private[this] def join(sk: SessionKey): Unit = {
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

        sender ! ActivityJoinSuccess()
    }
  }

  private[this] def leave(sk: SessionKey): Unit = {
    if (!isJoined(sk)) {
      throw throw new IllegalStateException("Session be joined to activity in order to leave.")
    } else {
      leaveHelper(sk)
      sender ! ActivityLeaveSuccess()
    }
  }

  private[this] def leaveHelper(sk: SessionKey): Unit = {
    val leaver = this.openedSessions(sk)
    val message = ActivitySessionLeft(activityId, sk)
    openedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sk)
    this.joinedSessions -= sk
    this.openedClients -= leaver
  }

  private[this] def setState(sk: SessionKey, key: String, value: Any): Unit = {
    if (isJoined(sk)) {
      stateMap.setState(sk, key, value)
      val setter = this.openedSessions(sk)
      val message = ActivityRemoteStateSet(activityId, sk, key, value)
      openedSessions.values.filter(_ != setter) foreach (_ ! message)
    }
  }

  private[this] def clearState(sk: SessionKey, key: String): Unit = {
    if (isJoined(sk)) {
      stateMap.clearState(sk, key)
      val clearer = this.openedSessions(sk)
      val message = ActivityRemoteStateCleared(activityId, sk, key)
      openedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def handleClientDeath(actor: ActorRef): Unit = {
    this.openedClients.get(actor) match {
      case Some(sk) =>
        log.debug(s"Client with session ${sk.serialize()} was stopped.  Leaving activity.")
        this.leaveHelper(sk)
      case None =>
        log.warning("Deathwatch on a client was triggered for an actor that did not have thi activity open")
    }
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
