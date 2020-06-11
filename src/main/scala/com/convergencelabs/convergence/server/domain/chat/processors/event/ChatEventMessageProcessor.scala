/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat.processors.event

import java.time.Instant

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{ChatEventRequest, CommonErrors}
import com.convergencelabs.convergence.server.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatState}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

trait ChatEventMessageProcessor[M <: ChatEventRequest[R], E, R] extends Logging {
  protected def timestamp(): Instant = Instant.now()

  protected def nextEvent(state: ChatState): Long = state.lastEventNumber + 1

  protected def replyAndBroadcastTask(replyTo: ActorRef[R], reply: R, broadcast: Option[ChatClientActor.OutgoingMessage] = None): ReplyAndBroadcastTask = {
    ReplyAndBroadcastTask(MessageReplyTask[R](replyTo, reply), broadcast)
  }

  def process(state: ChatState,
              message: M,
              checkPermissions: DomainUserId => Try[Boolean],
              validateMessage: (M, ChatState) => Either[R, Unit],
              createEvent: (M, ChatState) => E,
              processEvent: E => Try[Unit],
              updateState: (E, ChatState) => ChatState,
              createSuccessReply: (M, E) => ReplyAndBroadcastTask,
              createErrorReply: CommonErrors => R): ChatEventMessageProcessorResult = {
    val replyTo = message.replyTo
    checkPermissions(message.requester) match {
      case Failure(_) =>
        ChatEventMessageProcessorResult(None, replyAndBroadcastTask(replyTo, createErrorReply(ChatActor.UnknownError())))
      case Success(false) =>
        ChatEventMessageProcessorResult(None, replyAndBroadcastTask(replyTo, createErrorReply(ChatActor.UnauthorizedError())))
      case Success(true) =>
        validateMessage(message, state) match {
          case Left(response) =>
            ChatEventMessageProcessorResult(None, replyAndBroadcastTask(replyTo, response))
          case Right(()) =>
            val event = createEvent(message, state)
            processEvent(event)
              .map { _ =>
                val action = createSuccessReply(message, event)
                val newState = updateState(event, state)
                ChatEventMessageProcessorResult(Some(newState), action)
              }
              .recover {
                case cause =>
                  logger.error("Unexpected error processing chat event request", cause)
                  ChatEventMessageProcessorResult(None, replyAndBroadcastTask(replyTo, createErrorReply(ChatActor.UnknownError())))
              }.get
        }

    }
  }

  def execute(message: M, state: ChatState, chatStore: ChatStore, permissionsStore: PermissionsStore): ChatEventMessageProcessorResult

  def validateMessage(message: M, state: ChatState): Either[R, Unit]

  def createEvent(message: M, state: ChatState): E

  def processEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: E): Try[Unit]

  def updateState(event: E, state: ChatState): ChatState

  def createSuccessReply(state: ChatState)(message: M, event: E): ReplyAndBroadcastTask

  def createErrorReply(error: CommonErrors): R
}
