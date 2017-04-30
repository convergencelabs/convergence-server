package com.convergencelabs.server.domain.chat

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.chat.ChatChannelActor.Stop
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.cluster.sharding.ShardRegion.Passivate

class ChatRoomMessageProcessor(
  stateManager: ChatChannelStateManager,
  context: ActorContext)
    extends ChatChannelMessageProcessor(stateManager) {
  
  val chatRoomSessionManager = new ChatRoomSessionManager()

  context.system.actorOf(Props(new Watcher()))

  override def processChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(new InvalidChannelMessageExcpetion("Can not add user to a chat room"))
      case _: ExistingChannelMessage =>
        super.processChatMessage(message)
    }
  }

  override def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(channelId, sk, client) = message
    chatRoomSessionManager.join(sk, client) match {
      case true =>
        // First session in, process the join request normally
        super.onJoinChannel(message)
      case false =>
        // user is already in, so short circuit
        Success(ChatMessageProcessingResult(Some(createJoinResponse()), List()))
    }
  }

  override def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(channelId, sk, client) = message
    val result = chatRoomSessionManager.leave(sk) match {
      case true =>
        super.onLeaveChannel(message)
      case false =>
        // Use has more sessions, so no need to broadcast anything, or change state
        Success(ChatMessageProcessingResult(Some(()), List()))
    }

    // TODO maybe make this a call back
    if (stateManager.state().members.isEmpty) {
      context.parent ! Passivate(stopMessage = Stop)
    }

    result
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
            stateManager.onLeaveChannel(sk.uid)
          }
        }
    }
  }
}