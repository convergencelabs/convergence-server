package com.convergencelabs.server.domain.chat

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.chat.ChatChannelActor.Stop
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.cluster.sharding.ShardRegion.Passivate
import grizzled.slf4j.Logging

class ChatRoomMessageProcessor(
  domainFqn: DomainFqn,
  channelId: String,
  stateManager: ChatChannelStateManager,
  context: ActorContext)
    extends ChatChannelMessageProcessor(stateManager)
    with Logging {

  private[this] val chatRoomSessionManager = new ChatRoomSessionManager()
  private[this] val watcher = context.system.actorOf(Props(new Watcher()))

  override def processChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(new InvalidChannelMessageExcpetion("Can not add user to a chat room"))
      case _: ExistingChannelMessage =>
        super.processChatMessage(message)
    }
  }

  override def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(domainFqn, channelId, sk, client) = message
    logger.debug(s"Client(${sk}) joined chat room: ${channelId}")
    watcher.tell(client, Actor.noSender)

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
    val LeaveChannelRequest(domainFqn, channelId, sk, client) = message
    logger.debug(s"Client(${sk}) left chat room: ${channelId}")
    val result = chatRoomSessionManager.leave(sk) match {
      case true =>
        super.onLeaveChannel(message)
      case false =>
        // User has more sessions, so no need to broadcast anything, or change state
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

  /**
   * A helper actor that watches chat clients and helps notify us that a client
   * has left.
   */
  class Watcher() extends Actor with ActorLogging {
    def receive = {
      case client: ActorRef =>
        context.watch(client)
      case Terminated(client) =>
        context.unwatch(client)
        chatRoomSessionManager.getSession(client).foreach { sk =>
          // TODO This is a little sloppy since we will send a message to the client, which we already know is gone.
          val syntheticMessage = LeaveChannelRequest(domainFqn, channelId, sk, client)
          processChatMessage(syntheticMessage) recover {
            case cause: Throwable => 
              log.error(cause, "Error leaving channel after clinet actor terminated")
          }
        }
    }
  }
}