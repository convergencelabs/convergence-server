package com.convergencelabs.server.domain.activity

import scala.util.Success
import scala.util.Try

import org.json4s.JsonAST.JValue

import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.domain.DomainId

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
  private[this] var domain: DomainId = _

  private[this] var joinedClients = Map[ActorRef, String]()
  private[this] var joinedSessions = Map[String, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  override protected def setIdentityData(message: IncomingActivityMessage): Try[String] = {
    this.activityId = message.activityId
    this.domain = message.domain
    Success(s"${domain.namespace}/${domain.domainId}/${this.activityId}")
  }

  override def initialize(message: IncomingActivityMessage): Try[ShardedActorStatUpPlan] = {
    Success(StartUpRequired)
  }

  override def receiveInitialized: Receive = {
    case ActivityParticipantsRequest(domain, id) =>
      participantsRequest()
    case ActivityJoinRequest(domain, id, sessionId, state, client) =>
      join(sessionId, state, client)
    case ActivityLeave(domain, id, sessionId) =>
      leave(sessionId)
    case ActivitySetState(domain, id, sessionId, state) =>
      setState(sessionId, state)
    case ActivityRemoveState(domain, id, sessionId, keys) =>
      removeState(sessionId, keys)
    case ActivityClearState(domain, id, sessionId) =>
      clearState(sessionId)
    case Terminated(actor) =>
      handleClientDeath(actor)
  }

  private[this] def isEmpty(): Boolean = {
    joinedSessions.isEmpty
  }

  private[this] def participantsRequest(): Unit = {
    sender ! ActivityParticipants(stateMap.getState())
  }

  private[this] def getJoinedSessions(): Map[String, ActorRef] = {
    this.joinedSessions
  }

  private[this] def isJoined(sessionId: String): Boolean = {
    this.joinedSessions.contains(sessionId)
  }

  private[this] def join(sessionId: String, state: Map[String, JValue], client: ActorRef): Unit = {
    this.joinedSessions.get(sessionId) match {
      case Some(x) =>
        this.sender ! Status.Failure(ActivityAlreadyJoinedException(this.activityId))
      case None =>
        this.joinedSessions += (sessionId -> client)
        this.joinedClients += (client -> sessionId)
        this.stateMap.join(sessionId)

        state.foreach {
          case (k, v) =>
            this.stateMap.setState(sessionId, k, v)
        }

        context.watch(client)

        val message = ActivitySessionJoined(activityId, sessionId, state)
        joinedSessions.values filter (_ != client) foreach (_ ! message)

        sender ! ActivityJoinResponse(stateMap.getState())
    }
  }

  private[this] def leave(sessionId: String): Unit = {
    if (!isJoined(sessionId)) {
      this.sender ! Status.Failure(ActivityNotJoinedException(this.activityId))
    } else {
      leaveHelper(sessionId)
    }
  }

  private[this] def leaveHelper(sessionId: String): Unit = {
    val leaver = this.joinedSessions(sessionId)
    val message = ActivitySessionLeft(activityId, sessionId)
    joinedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sessionId)
    this.joinedSessions -= sessionId
    this.joinedClients -= leaver

    this.context.unwatch(leaver)

    if (this.joinedSessions.isEmpty) {
      this.passivate()
    }
  }

  private[this] def setState(sessionId: String, state: Map[String, JValue]): Unit = {
    if (isJoined(sessionId)) {
      state.foreach {
        case (key: String, value: Any) =>
          stateMap.setState(sessionId, key, value)
      }

      val setter = this.joinedSessions(sessionId)
      val message = ActivityRemoteStateSet(activityId, sessionId, state)
      joinedSessions.values.filter(_ != setter) foreach (_ ! message)
    }
  }

  private[this] def removeState(sessionId: String, keys: List[String]): Unit = {
    if (isJoined(sessionId)) {
      keys foreach (stateMap.clearState(sessionId, _))
      val clearer = this.joinedSessions(sessionId)
      val message = ActivityRemoteStateRemoved(activityId, sessionId, keys)
      joinedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def clearState(sessionId: String): Unit = {
    if (isJoined(sessionId)) {
      val clearer = this.joinedSessions(sessionId)
      val message = ActivityRemoteStateCleared(activityId, sessionId)
      joinedSessions.values.filter(_ != clearer) foreach (_ ! message)
    }
  }

  private[this] def handleClientDeath(actor: ActorRef): Unit = {
    this.joinedClients.get(actor) match {
      case Some(sessionId) =>
        log.debug(s"${identityString}: Client with session ${sessionId} was terminated.  Leaving activity.")
        this.leaveHelper(sessionId)
      case None =>
        log.warning(s"${identityString}: Deathwatch on a client was triggered for an actor that did not have thi activity open")
    }
  }
}