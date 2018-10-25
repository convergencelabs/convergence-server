package com.convergencelabs.server.domain.activity

import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.Terminated

object ActivityActor {
  def props(): Props = {
    Props(new ActivityActor())
  }
}

class ActivityActor()
  extends ShardedActor(classOf[IncomingActivityMessage])
  with ActorLogging {

  /**
   * Will be initialized on the first message inside the initialize method.
   */
  private[this] var activityId: String = _
  private[this] var domain: DomainFqn = _

  private[this] var joinedClients = Map[ActorRef, SessionKey]()
  private[this] var joinedSessions = Map[SessionKey, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  override def initialize(message: IncomingActivityMessage): Try[ShardedActorStatUpPlan] = {
    this.activityId = message.activityId
    this.domain = message.domain
    log.debug( s"${activityToString} initiaizlized")
    Success(StartUpRequired)
  }

  override def receiveInitialized: Receive = {
    case ActivityParticipantsRequest(domain, id) =>
      participantsRequest()
    case ActivityJoinRequest(domain, id, sk, state, client) =>
      join(sk, state, client)
    case ActivityLeave(domain, id, sk) =>
      leave(sk)
    case ActivitySetState(domain, id, sk, state) =>
      setState(sk, state)
    case ActivityRemoveState(domain, id, sk, keys) =>
      removeState(sk, keys)
    case ActivityClearState(domain, id, sk) =>
      clearState(sk)
    case Terminated(actor) =>
      handleClientDeath(actor)
  }
  
  override def postStop(): Unit = {
    log.debug(s"${activityToString} stopped")
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
        this.sender ! Status.Failure(ActivityAlreadyJoinedException(this.activityId))
      case None =>
        this.joinedSessions += (sk -> client)
        this.joinedClients += (client -> sk)
        this.stateMap.join(sk)

        state.foreach {
          case (k, v) =>
            this.stateMap.setState(sk, k, v)
        }

        context.watch(client)

        val message = ActivitySessionJoined(activityId, sk, state)
        joinedSessions.values filter (_ != client) foreach (_ ! message)

        sender ! ActivityJoinResponse(stateMap.getState())
    }
  }

  private[this] def leave(sk: SessionKey): Unit = {
    if (!isJoined(sk)) {
      this.sender ! Status.Failure(ActivityNotJoinedException(this.activityId))
    } else {
      leaveHelper(sk)
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

    if (this.joinedSessions.isEmpty) {
      this.passivate()
    }
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

  private[this] def removeState(sk: SessionKey, keys: List[String]): Unit = {
    if (isJoined(sk)) {
      keys foreach (stateMap.clearState(sk, _))
      val clearer = this.joinedSessions(sk)
      val message = ActivityRemoteStateRemoved(activityId, sk, keys)
      joinedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def clearState(sk: SessionKey): Unit = {
    if (isJoined(sk)) {
      val clearer = this.joinedSessions(sk)
      val message = ActivityRemoteStateCleared(activityId, sk)
      joinedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def handleClientDeath(actor: ActorRef): Unit = {
    this.joinedClients.get(actor) match {
      case Some(sk) =>
        log.debug(s"Client with session ${sk.serialize()} was terminated.  Leaving activity.")
        this.leaveHelper(sk)
      case None =>
        log.warning("Deathwatch on a client was triggered for an actor that did not have thi activity open")
    }
  }
  
  private[this] def activityToString() =
    s"Activity(${domain.namespace}/${domain.domainId}/${this.activityId})"
}