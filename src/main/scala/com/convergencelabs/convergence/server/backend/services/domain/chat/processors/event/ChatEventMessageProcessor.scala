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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors.event

import java.time.Instant

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{ChatEventRequest, CommonErrors}
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatState}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

/**
 * A helper class to process ChatEventRequest object from the ChatActor. A
 * subclass of ths trait is provided for each subclass of ChatEventRequest.
 *
 * @tparam M The specific ChatEventRequest subclass this processor will handle.
 * @tparam E The type of chat event to be created and processed from the request.
 * @tparam R The type of response message to be generated.
 */
private[chat] trait ChatEventMessageProcessor[M <: ChatEventRequest[R], E, R] extends Logging {

  /**
   * A helper method to create a timestamp for the current event.
   *
   * @return now
   */
  protected def timestamp(): Instant = Instant.now()

  /**
   * A helper method to fetch the event number for the next event.
   *
   * @param state The current state of the chat.
   * @return The event number to use for the event to be created.
   */
  protected def nextEvent(state: ChatState): Long = state.lastEventNumber + 1

  /**
   * Processes the ChatEventRequest message and produces a result.  The result
   * contains a potentially new state for the Chat, a response message to send
   * back to the request, and a broadcast message to send to all members of
   * the chat.
   *
   * @param message          The message to process.
   * @param state            The current state of the chat.
   * @param chatStore        The chat store to use to process the chat events.
   * @param permissionsStore The permissions store to verify the requester has
   *                         the correct permissions and also to potentially
   *                         update permissions based on the event.
   * @return A [[ChatEventMessageProcessorResult]] which contains a new chat
   *         state object if the result of the processing should alter the
   *         chat's state. The result will also container a reply message
   *         to send back to the requester as well as a potential broadcast
   *         message that should be sent to the members of the chat.
   */
  def execute(message: M,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[R]

  /**
   * Validates that the request is valid given the current state of the chat.
   *
   * @param message The request being processed.
   * @param state   The current state of the Chat.
   * @return Right if the message is valid. Left containing an error response to
   *         send back to the requester.
   */
  def validateMessage(message: M, state: ChatState): Either[R, Unit]

  /** '
   * Creates a Chat Event or other intermediate object that represents how
   * to update the chat based on the request and the current state.
   *
   * @param message The incoming request message.
   * @param state   The current state of the chat.
   * @return A result describing how the chat should be modified.
   */
  def createEvent(message: M, state: ChatState): E

  /**
   * Persists the event. This is likely a side-effecting method, that in
   * production will hit a data store. Hence the Try return type.
   *
   * @param chatStore        The chat store to use to persist effects to
   *                         relating to the chat.
   * @param permissionsStore The permissions store to persist effects relating
   *                         to permissions.
   * @param event            The event describing the action to take.
   * @return Success if the processing succeeds or a Failure otherwise.
   */
  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: E): Try[Unit]

  /**
   * Produces a new state from the current state and the event describing the
   * action to take.
   *
   * @param event The event to use to compute the new state from.
   * @param state The current state of the chat.
   * @return A new state that incorporates the effects of the event.
   */
  def updateState(event: E, state: ChatState): ChatState

  /**
   * Creates a response message to the requester when processing the message
   * succeeds.
   *
   * @param message The original request.
   * @param event   The event that was computed.
   * @param state   The new state that incorporated the effects of the event
   *                if any.
   * @return A successful response message.
   */
  def createSuccessReply(message: M, event: E, state: ChatState): ReplyAndBroadcastTask[R]

  /**
   * Creates an error response to send to the requester in the case of a
   * processing failure.
   *
   * @param error The generic error to respond with.
   * @return A response specific class that wraps the generic error.
   */
  def createErrorReply(error: CommonErrors): R
}

object ChatEventMessageProcessor extends Logging {
  /**
   * A helper method to create a ReplyAndBroadcastTask.
   *
   * @param replyTo   The actor to reply to.
   * @param reply     The message to reply to the requester.
   * @param broadcast An optional message to broadcast to all members
   *                  of the chat.
   * @tparam R The type of response message.
   * @return A constructed ReplyAndBroadcastTask object.
   */
  def replyAndBroadcastTask[R](replyTo: ActorRef[R],
                               reply: R,
                               broadcast: Option[ChatClientActor.OutgoingMessage] = None): ReplyAndBroadcastTask[R] = {
    ReplyAndBroadcastTask(MessageReplyTask[R](replyTo, reply), broadcast)
  }

  /**
   * A helper method that implements the main processing logic for the
   * ChatEventMessageProcessor. This method is broken out so that it can
   * be more purely functional and tested independently.
   *
   * @param message            The request message to process.
   * @param state              The current state of the chat.
   * @param checkPermissions   A function that checks to see if the
   *                           requester has sufficient permissions to
   *                           perform the requested action.
   * @param validateMessage    A function to validate the message based on
   *                           the current sate of the chat.
   * @param createEvent        A function that creates an event that describes
   *                           how the chat should be updated based on the
   *                           request message.
   * @param persistEvent       A function that persists the event.
   * @param updateState        A function to compute a potentially new state
   *                           for the chat based on the event.
   * @param createSuccessReply A function that constructs a successful reply
   *                           from the request message, the created event and
   *                           the updated state of the chat.
   * @param createErrorReply   A function that will create a correct wrapped
   *                           response message containing a generic error.
   *
   * @tparam M The specific ChatEventRequest subclass this processor will handle.
   * @tparam E The type of chat event to be created and processed from the request.
   * @tparam R The type of response message to be generated.
   *
   * @return The results of processing the message, given the state of the chat.
   */
  def process[M <: ChatEventRequest[R], E, R](message: M,
                                              state: ChatState,
                                              checkPermissions: DomainUserId => Try[Boolean],
                                              validateMessage: (M, ChatState) => Either[R, Unit],
                                              createEvent: (M, ChatState) => E,
                                              persistEvent: E => Try[Unit],
                                              updateState: (E, ChatState) => ChatState,
                                              createSuccessReply: (M, E, ChatState) => ReplyAndBroadcastTask[R],
                                              createErrorReply: CommonErrors => R): ChatEventMessageProcessorResult[R] = {
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
            persistEvent(event)
              .map { _ =>
                val newState = updateState(event, state)
                val action = createSuccessReply(message, event, newState)
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
}
