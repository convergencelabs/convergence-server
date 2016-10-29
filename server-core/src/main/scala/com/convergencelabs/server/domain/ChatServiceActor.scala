package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import PresenceServiceActor._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.convergencelabs.server.util.SubscriptionMap
import akka.actor.Terminated
import com.convergencelabs.server.domain.ChatServiceActor.LeaveRoom
import com.convergencelabs.server.domain.ChatServiceActor.SendMessage
import com.convergencelabs.server.domain.ChatServiceActor.UserLeft
import com.convergencelabs.server.domain.ChatServiceActor.UserJoined
import java.util.Calendar
import com.convergencelabs.server.domain.ChatServiceActor.UserMessage
import com.convergencelabs.server.domain.ChatServiceActor.JoinRoomRequest
import com.convergencelabs.server.domain.ChatServiceActor.JoinRoomResponse
import java.time.Instant

object ChatServiceActor {

  val RelativePath = "chatService"

  def props(domainFqn: DomainFqn): Props = Props(
    new ChatServiceActor(domainFqn))

  case class JoinRoomRequest(client: ActorRef, roomId: String, sk: SessionKey)
  case class JoinRoomResponse(members: List[SessionKey], messageCount: Long, lastMessageTime: Long)
  case class LeaveRoom(client: ActorRef, roomId: String)
  case class SendMessage(client: ActorRef, roomId: String, message: String)
  
  case class UserJoined(roomId: String, sk: SessionKey, timestamp: Long)
  case class UserLeft(roomId: String, sk: SessionKey, timestamp: Long)
  case class UserMessage(roomId: String, sk: SessionKey, message: String, timestamp: Long)
 
}

class ChatServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private var chatRooms = Map[String, List[String]]()
  
  // FIXME These are particularly problematic.  We are storing actor refs and usernames
  // in memory.  there is not a good way to persist this.
  private var subscriptions = SubscriptionMap[ActorRef, String]()
  private var clients = Map[ActorRef, SessionKey]()

  
  def receive: Receive = {
    case JoinRoomRequest(client, roomId, sk) =>
      joinRoom(client, roomId, sk)
    case LeaveRoom(client, roomId) =>
      leaveRoom(client, roomId)
    case SendMessage(client, roomId, message) =>
      sendMessage(client, roomId, message)
    case Terminated(client) =>
      handleDeathwatch(client)
  }

  private[this] def joinRoom(client: ActorRef, roomId: String, sk: SessionKey): Unit = {
    subscriptions.subscribe(client, roomId)
    clients += (client -> sk)
    broadcastToSubscribed(client, roomId, UserJoined(roomId, sk, Calendar.getInstance().getTimeInMillis()))
    sender ! JoinRoomResponse(subscriptions.subscribers(roomId).map { clients(_) }.toList, 0, Instant.now().toEpochMilli())
  }

  private[this] def leaveRoom(client: ActorRef, roomId: String): Unit = {
    clients.get(client) match {
      case Some(sk) => {
        if(subscriptions.isSubscribed(client, roomId)) {
          subscriptions.unsubscribe(client, roomId)
          if(subscriptions.subscriptions(client).isEmpty) {
            clients -= client
          }
          broadcastToSubscribed(client, roomId, UserLeft(roomId, sk, Calendar.getInstance().getTimeInMillis()))
        }
      }
      case None =>
    }
  }

  private[this] def sendMessage(client: ActorRef, roomId: String, message: String): Unit = {
    clients.get(client) match {
      case Some(sk) => {
        if(subscriptions.isSubscribed(client, roomId)) {
          broadcastToSubscribed(client, roomId, UserMessage(roomId, sk, message, Calendar.getInstance().getTimeInMillis()))
        }
      }
      case None =>
    }
  }

  private[this] def broadcastToSubscribed(sender: ActorRef,  roomId: String, message: Any): Unit = {
    subscriptions.subscribers(roomId).filter { client => client != sender } foreach { client =>
      client ! message
    }
  }
  
  private[this] def handleDeathwatch(client: ActorRef): Unit = {
    subscriptions.unsubscribe(client)
    clients -= client
  }
}
