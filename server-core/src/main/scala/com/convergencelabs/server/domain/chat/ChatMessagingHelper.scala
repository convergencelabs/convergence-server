package com.convergencelabs.server.domain.chat

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest

import akka.actor.ActorContext
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.actor.Actor
import akka.actor.Terminated
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ShardRegion.Passivate
import com.convergencelabs.server.domain.chat.ChatChannelActor.Stop

trait ChatMessagingHelper {

  def validateMessage(message: ExistingChannelMessage): Try[ExistingChannelMessage]
  def preProcessMessage(message: ExistingChannelMessage): Either[ExistingChannelMessage, Any]
  def boradcast(message: Any): Unit
}

class ChatRoomMessagingHelper(channelManager: ChatChannelStateManager, context: ActorContext) extends ChatMessagingHelper {
  val chatRoomSessionManager = new ChatRoomSessionManager()
  
  context.system.actorOf(Props(new Watcher()))

  def validateMessage(message: ExistingChannelMessage): Try[ExistingChannelMessage] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(new InvalidChannelMessageExcpetion("Can not add user to a chat room"))
      case _: ExistingChannelMessage =>
        Success(message)
    }
  }

  def preProcessMessage(message: ExistingChannelMessage): Either[ExistingChannelMessage, Any] = {
    val pass = message match {
      case JoinChannelRequest(channelId, sk, client) =>
        chatRoomSessionManager.join(sk, client)
        
      case LeaveChannelRequest(channelId, sk, client) =>
        chatRoomSessionManager.leave(sk)
        // TODO maybe make this a call back
        if (channelManager.state().members.isEmpty) {
          context.parent ! Passivate(stopMessage = Stop)
        }
      case RemoveUserFromChannelRequest(channelId, username, removedBy) =>
        chatRoomSessionManager.remove(username)
        true
      case _ =>
        true
    }

    pass match {
      case true => Left(message)
      case false => Right()
    }
  }

  def boradcast(message: Any): Unit = {
    chatRoomSessionManager.connectedClients().foreach(client => {
      client ! message
    })
  }

  class Watcher() extends Actor {
    def receive = {
      case client: ActorRef =>
        context.watch(client)
      case Terminated(client) =>
        val generateMessage = chatRoomSessionManager.leave(client)
        if (generateMessage) {
          chatRoomSessionManager.getSession(client).foreach { sk =>
            channelManager.onLeaveChannel(sk.uid)
          }
        }
    }
  }
}

abstract class MembershipChannelMessageHelper(channelManager: ChatChannelStateManager, context: ActorContext) extends ChatMessagingHelper {
  val mediator = DistributedPubSub(context.system).mediator

  def validateMessage(message: ExistingChannelMessage): Try[ExistingChannelMessage]

  def preProcessMessage(message: ExistingChannelMessage): Either[ExistingChannelMessage, Any] = Left(message)

  def boradcast(message: Any): Unit = {
    val members = channelManager.state().members
    members.foreach { member =>
      val topic = ChatChannelActor.getChatUsernameTopicName(member)
      mediator ! Publish(topic, message)
    }
  }
}

class DirectChannelMessagingHelper(channelManager: ChatChannelStateManager, context: ActorContext)
    extends MembershipChannelMessageHelper(channelManager, context) {

  def validateMessage(message: ExistingChannelMessage): Try[ExistingChannelMessage] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not add user to a direct channel"))
      case _: RemoveUserFromChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not remove a user from a direct channel"))
      case _: JoinChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not join a direct channel"))
      case _: LeaveChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not leave a direct channel"))
      case _: ExistingChannelMessage =>
        Success(message)
    }
  }
}

class GroupChannelMessagingHelper(channelManager: ChatChannelStateManager, context: ActorContext)
    extends MembershipChannelMessageHelper(channelManager, context) {
  def validateMessage(message: ExistingChannelMessage): Try[ExistingChannelMessage] = Success(message)
}
