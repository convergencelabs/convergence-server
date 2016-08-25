package com.convergencelabs.server.domain

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionJoined
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionLeft
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateSet
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateCleared
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySetState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityClearState
import akka.actor.Status
import akka.actor.Terminated
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipantsRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipants
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoin
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeave

object ActivityActor {
  def props(activityId: String): Props = Props(
    new ActivityActor(activityId))
}

private[domain] class ActivityActor(private[this] val activityId: String)
    extends Actor with ActorLogging {

  private[this] var joinedClients = Map[ActorRef, SessionKey]()
  private[this] var joinedSessions = Map[SessionKey, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  def receive: Receive = {
    case ActivityParticipantsRequest(id) => participantsRequest()
    case ActivityJoin(id, sk, state, client) => join(sk, state, client)
    case ActivityLeave(id, sk) => leave(sk)
    case ActivitySetState(id, sk, state) => setState(sk, state)
    case ActivityClearState(id, sk, keys) => clearState(sk, keys)
    case Terminated(actor) => handleClientDeath(actor)
  }

  private[this] def isEmpty(): Boolean = {
    joinedSessions.isEmpty
  }

  private[this] def participantsRequest(): Unit = {
    sender ! ActivityParticipants(stateMap.getState())
  }

  private[this] def getJoinedSessions(): Map[SessionKey, ActorRef] = {
    this.joinedSessions
  }

  private[this] def isJoined(sk: SessionKey): Boolean = {
    this.joinedSessions.contains(sk)
  }

  private[this] def join(sk: SessionKey, state: Map[String, Any], client: ActorRef): Unit = {
    this.joinedSessions.get(sk) match {
      case Some(x) =>
        throw new IllegalStateException("Session already joined")
      case None =>
        state.foreach {
          case (k, v) =>
            this.stateMap.setState(sk, k, v)
        }

        val message = ActivitySessionJoined(activityId, sk, state)
        joinedSessions.values filter (_ != client) foreach (_ ! message)

        this.joinedSessions += (sk -> client)
        this.joinedClients += (client -> sk)
        this.stateMap.join(sk)

        context.watch(client)

      //sender ! ActivityJoinSuccess()
    }
  }

  private[this] def leave(sk: SessionKey): Unit = {
    if (!isJoined(sk)) {
      throw throw new IllegalStateException("Session be joined to activity in order to leave.")
    } else {
      leaveHelper(sk)
      //sender ! ActivityLeaveSuccess()
    }
  }

  private[this] def leaveHelper(sk: SessionKey): Unit = {
    val leaver = this.joinedSessions(sk)
    val message = ActivitySessionLeft(activityId, sk)
    joinedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sk)
    this.joinedSessions -= sk
    this.joinedClients -= leaver
    
    this.context.unwatch(leaver)
  }

  private[this] def setState(sk: SessionKey, state: Map[String, Any]): Unit = {
    if (isJoined(sk)) {
      state.foreach {
        case (key: String, value: Any) =>
          stateMap.setState(sk, key, value)
      }

      val setter = this.joinedSessions(sk)
      val message = ActivityRemoteStateSet(activityId, sk, state)
      joinedSessions.values.filter(_ != setter) foreach (_ ! message)
    }
  }

  private[this] def clearState(sk: SessionKey, keys: List[String]): Unit = {
    if (isJoined(sk)) {
      keys foreach (stateMap.clearState(sk, _))
      val clearer = this.joinedSessions(sk)
      val message = ActivityRemoteStateCleared(activityId, sk, keys)
      joinedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def handleClientDeath(actor: ActorRef): Unit = {
    this.joinedClients.get(actor) match {
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
