package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.PresenceServiceActor.PresenceRequest
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresence
import com.convergencelabs.server.domain.PresenceServiceActor.SubscribePresence
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresencePublishState
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresenceClearState
import com.convergencelabs.server.domain.PresenceServiceActor.UnsubscribePresence
import com.convergencelabs.server.domain.PresenceServiceActor.UserConnected
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresenceAvailability

object PresenceClientActor {
  def props(presenceServiceActor: ActorRef, sk: SessionKey): Props =
    Props(new PresenceClientActor(presenceServiceActor, sk))
}

//  TODO: Add connect / disconnect logic
class PresenceClientActor(presenceServiceActor: ActorRef, sk: SessionKey) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher
  
  presenceServiceActor ! UserConnected(sk.uid, self)

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[IncomingPresenceNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingPresenceNormalMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingPresenceRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingPresenceRequestMessage], replyPromise)

    // TODO: Add available messages
    case UserPresencePublishState(username, key, value) =>
      context.parent ! PresenceStateSetMessage(username, key, value)
    case UserPresenceClearState(username, key) =>
      context.parent ! PresenceStateClearedMessage(username, key)
    case UserPresenceAvailability(username, available) =>
      context.parent ! PresenceAvailabilityChangedMessage(username, available)
      
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingPresenceNormalMessage): Unit = {
    message match {
      case setState: PresenceSetStateMessage => onPresenceStateSet(setState)
      case clearState: PresenceClearStateMessage => onPresenceStateCleared(clearState)
      case unsubPresence: UnsubscribePresenceMessage => onUnsubscribePresence(unsubPresence)
    }
  }

  def onPresenceStateSet(message: PresenceSetStateMessage): Unit = {
    val PresenceSetStateMessage(key, value) = message
    this.presenceServiceActor ! UserPresencePublishState(sk.uid, key, value)
  }

  def onPresenceStateCleared(message: PresenceClearStateMessage): Unit = {
    val PresenceClearStateMessage(key) = message
    this.presenceServiceActor ! UserPresenceClearState(sk.uid, key)
  }
  
  def onUnsubscribePresence(message: UnsubscribePresenceMessage): Unit = {
    val UnsubscribePresenceMessage(subUsername) = message
    this.presenceServiceActor ! UnsubscribePresence(subUsername, self)
  }
  

  def onRequestReceived(message: IncomingPresenceRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case presenceReq: PresenceRequestMessage => onPresenceRequest(presenceReq, replyCallback)
      case subscribeReq: SubscribePresenceRequestMessage => onSubscribeRequest(subscribeReq, replyCallback)
    }
  }

  def onPresenceRequest(request: PresenceRequestMessage, cb: ReplyCallback): Unit = {
    val PresenceRequestMessage(usernames) = request
    val future = this.presenceServiceActor ? PresenceRequest(usernames)

    future.mapResponse[List[UserPresence]] onComplete {
      case Success(userPresences) =>
        cb.reply(PresenceResponseMessage(userPresences))
      case Failure(cause) =>
        cb.unexpectedError("Could not retrieve presence")
    }
  }

  def onSubscribeRequest(request: SubscribePresenceRequestMessage, cb: ReplyCallback): Unit = {
    val SubscribePresenceRequestMessage(username) = request
    val future = this.presenceServiceActor ? SubscribePresence(username, self)

    future.mapResponse[UserPresence] onComplete {
      case Success(userPresence) =>
        cb.reply(SubscribePresenceResponseMessage(userPresence))
      case Failure(cause) =>
        cb.unexpectedError("Could not subscribe to presence")
    }
  }
}
