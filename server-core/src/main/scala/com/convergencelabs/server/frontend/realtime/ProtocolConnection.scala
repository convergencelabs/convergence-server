package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeoutException

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.ProtocolConfiguration

import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Scheduler
import akka.actor.actorRef2Scala
import grizzled.slf4j.Logging
import convergence.protocol.Outgoing
import scalapb.GeneratedMessage
import convergence.protocol.Request
import convergence.protocol.Response
import convergence.protocol.Incoming
import convergence.protocol.message.MessageEnvelope.Body
import convergence.protocol.connection._
import convergence.protocol.model._
import convergence.protocol.activity._
import convergence.protocol.authentication._
import convergence.protocol.model._
import convergence.protocol.message._
import convergence.protocol.chat._
import convergence.protocol.permissions._
import convergence.protocol.presence._
import convergence.protocol.references._
import convergence.protocol.identity._
import convergence.protocol.operations._


sealed trait ProtocolMessageEvent {
  def message: Incoming
}

case class MessageReceived(message: Incoming) extends ProtocolMessageEvent
case class RequestReceived(message: Request, replyCallback: ReplyCallback) extends ProtocolMessageEvent

class ProtocolConnection(
  private[this] val clientActor:     ActorRef,
  private[this] val connectionActor: ActorRef,
  private[this] val protocolConfig:  ProtocolConfiguration,
  private[this] val scheduler:       Scheduler,
  private[this] val ec:              ExecutionContext)
  extends Logging {

  private[this] val heartbeatHelper = new HeartbeatHelper(
    protocolConfig.heartbeatConfig.pingInterval,
    protocolConfig.heartbeatConfig.pongTimeout,
    scheduler,
    ec,
    handleHeartbeat)

  if (protocolConfig.heartbeatConfig.enabled) {
    heartbeatHelper.start()
  }

  var nextRequestId = 0
  val requests = mutable.Map[Long, RequestRecord]()

  def send(message: Outgoing): Unit = {
    val envelope = message match {
      case message: ActivitySessionJoinedMessage =>
        convergence.protocol.message.MessageEnvelope()
      case _ =>
        ???
    }

    sendMessage(envelope)
  }

  def request(message: Request)(implicit executor: ExecutionContext): Future[Response] = {
    val requestId = nextRequestId
    nextRequestId += 1

    val replyPromise = Promise[GeneratedMessage with Response]

    val timeout = protocolConfig.defaultRequestTimeout
    val timeoutFuture = scheduler.scheduleOnce(timeout)(() => {
      requests.synchronized({
        requests.remove(requestId) match {
          case Some(record) => {
            record.promise.failure(new TimeoutException("Response timeout"))
          }
          case _ =>
          // Race condition where the reply just came in under the wire.
          // no action requried.
        }
      })
    })

    val envelope = message match {
      case message: AutoCreateModelConfigRequestMessage =>
        convergence.protocol.message.MessageEnvelope()
      case _ =>
        ???
    }

    sendMessage(envelope.withRequestId(requestId))

    requests(requestId) = RequestRecord(requestId, replyPromise, timeoutFuture)
    replyPromise.future
  }

  def handleClosed(): Unit = {
    logger.debug(s"Protocol connection closed")
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  def sendMessage(envelope: convergence.protocol.message.MessageEnvelope): Unit = {
    val bytes = envelope.toByteArray
    connectionActor ! OutgoingBinaryMessage(bytes)
    if (!envelope.body.isPing && !envelope.body.isPong) {
      logger.trace("S: " + envelope)
    }
  }

  def onIncomingMessage(message: Array[Byte]): Try[Option[ProtocolMessageEvent]] = {
    if (protocolConfig.heartbeatConfig.enabled) {
      heartbeatHelper.messageReceived()
    }

    convergence.protocol.message.MessageEnvelope.validate(message) match {
      case Success(envelope) =>
        handleValidMessage(envelope)
      case Failure(cause) =>
        val message = "Could not parse incoming protocol message"
        logger.error(message, cause)
        Failure(new IllegalArgumentException(message))
    }
  }

  // scalastyle:off cyclomatic.complexity
  private def handleValidMessage(envelope: convergence.protocol.message.MessageEnvelope): Try[Option[ProtocolMessageEvent]] = Try {
    if (!envelope.body.isPing && !envelope.body.isPong) {
      logger.trace("R: " + envelope)
    }

    toMessage(envelope.body) match {
      case _: PingMessage =>
        onPing()
        None
      case _: PongMessage =>
        // No Opo
        None
      case message: Incoming =>
        Some(MessageReceived(message))
      case message: Request =>
        Some(RequestReceived(message, new ReplyCallbackImpl(envelope.requestId.get)))
      case message: Response =>
        onReply(message, envelope.responseId.get)
        None
      case _ =>
        throw new IllegalArgumentException("Invalid message: " + envelope)
    }
  }
  // scalastyle:on cyclomatic.complexity

  def dispose(): Unit = {
    if (heartbeatHelper.started) {
      heartbeatHelper.stop()
    }
  }

  def toMessage(body: Body): GeneratedMessage = {
    body.value match {
      case Body.Error(message)                             => message
      case Body.Ping(message)                              => message
      case Body.Pong(message)                              => message
      case Body.HandshakeRequest(message)                  => message
      case Body.HandshakeResponse(message)                 => message
      case Body.PasswordAuthRequest(message)               => message
      case Body.TokenAuthRequest(message)                  => message
      case Body.AnonymousAuthRequest(message)              => message
      case Body.ReconnectAuthRequest(message)              => message
      case Body.AuthenticationResponse(message)            => message
      case Body.OpenRealTimeModelRequest(message)          => message
      case Body.OpenRealTimeModelResponse(message)         => message
      case Body.CloseRealTimeModelRequest(message)         => message
      case Body.CloseRealTimeModelResponse(message)        => message
      case Body.CreateRealTimeModelRequest(message)        => message
      case Body.CreateRealTimeModelResponse(message)       => message
      case Body.DeleteRealtimeModelRequest(message)        => message
      case Body.DeleteRealtimeModelResponse(message)       => message
      case Body.ForceCloseRealTimeModel(message)           => message
      case Body.RemoteClientOpenedModel(message)           => message
      case Body.RemoteClientClosedModel(message)           => message
      case Body.ModelAutoCreateConfigRequest(message)      => message
      case Body.ModelAutoCreateConfigResponse(message)     => message
      case Body.RemoteOperation(message)                   => message
      case Body.OperationSubmission(message)               => message
      case Body.OperationAck(message)                      => message
      case Body.PublishReference(message)                  => message
      case Body.SetReference(message)                      => message
      case Body.ClearReference(message)                    => message
      case Body.UnpublishReference(message)                => message
      case Body.ReferencePublished(message)                => message
      case Body.ReferenceSet(message)                      => message
      case Body.ReferenceCleared(message)                  => message
      case Body.ReferenceUnpublished(message)              => message
      case Body.ModelsQueryRequest(message)                => message
      case Body.ModelsQueryResponse(message)               => message
      case Body.HistoricalDataRequest(message)             => message
      case Body.HistoricalDataResponse(message)            => message
      case Body.HistoricalOperationsRequest(message)       => message
      case Body.HistoricalOperationsResponse(message)      => message
      case Body.UserLookUpRequest(message)                 => message
      case Body.UserSearchRequest(message)                 => message
      case Body.UserListResponse(message)                  => message
      case Body.UserGroupsRequest(message)                 => message
      case Body.UserGroupsResponse(message)                => message
      case Body.UserGroupsForUsersRequest(message)         => message
      case Body.UserGroupsForUsersResponse(message)        => message
      case Body.ActivityParticipantsRequest(message)       => message
      case Body.ActivityParticipantsResponse(message)      => message
      case Body.ActivityJoinRequest(message)               => message
      case Body.ActivityJoinResponse(message)              => message
      case Body.ActivityLeaveRequest(message)              => message
      case Body.ActivitySessionJoined(message)             => message
      case Body.ActivitySessionLeft(message)               => message
      case Body.ActivityLocalStateSet(message)             => message
      case Body.ActivityLocalStateRemoved(message)         => message
      case Body.ActivityLocalStateCleared(message)         => message
      case Body.ActivityRemoteStateSet(message)            => message
      case Body.ActivityRemoteStateRemoved(message)        => message
      case Body.ActivityRemoteStateCleared(message)        => message
      case Body.PresenceSetState(message)                  => message
      case Body.PresenceClearState(message)                => message
      case Body.PresenceStateSet(message)                  => message
      case Body.PresenceStateCleared(message)              => message
      case Body.PresenceRequest(message)                   => message
      case Body.PresenceResponse(message)                  => message
      case Body.PresenceSubscribeRequest(message)          => message
      case Body.PresenceSubscribeResponse(message)         => message
      case Body.PresenceUnsubscribe(message)               => message
      case Body.CreateChatChannelRequest(message)          => message
      case Body.CreateChatChannelResponse(message)         => message
      case Body.RemoveChatChannelRequest(message)          => message
      case Body.RemoveChatChannelResponse(message)         => message
      case Body.GetChatChannelsRequest(message)            => message
      case Body.GetChatChannelsResponse(message)           => message
      case Body.ChatChannelExistsRequest(message)          => message
      case Body.ChatChannelExistsResponse(message)         => message
      case Body.GetDirectChatChannelsRequest(message)      => message
      case Body.GetDirectChatChannelsResponse(message)     => message
      case Body.GetJoinedChatChannelsRequest(message)      => message
      case Body.GetJoinedChatChannelsResponse(message)     => message
      case Body.JoinChatChannelRequest(message)            => message
      case Body.JoinChatChannelResponse(message)           => message
      case Body.UserJoinedChatChannel(message)             => message
      case Body.LeaveChatChannelRequest(message)           => message
      case Body.LeaveChatChannelResponse(message)          => message
      case Body.UserLeftChatChannel(message)               => message
      case Body.AddUserToChatChannelRequest(message)       => message
      case Body.AddUserToChatChannelResponse(message)      => message
      case Body.UserAddedToChatChannel(message)            => message
      case Body.RemoveUserFromChatChannelRequest(message)  => message
      case Body.RemoveUserFromChatChannelResponse(message) => message
      case Body.UserRemovedFromChatChannel(message)        => message
      case Body.SetChatChannelNameRequest(message)         => message
      case Body.SetChatChannelNameResponse(message)        => message
      case Body.ChatChannelNameChanged(message)            => message
      case Body.SetChatChannelTopicRequest(message)        => message
      case Body.SetChatChannelTopicResponse(message)       => message
      case Body.ChatChannelTopicChanged(message)           => message
      case Body.MarkChatChannelEventsSeenRequest(message)  => message
      case Body.MarkChatChannelEventsSeenResponse(message) => message
      case Body.ChatChannelEventsMarkedSeen(message)       => message
      case Body.PublishChatMessageRequest(message)         => message
      case Body.PublishChatMessageResponse(message)        => message
      case Body.RemoteChatMessage(message)                 => message
      case Body.GetChatChannelHistoryRequest(message)      => message
      case Body.GetChatChannelHistoryResponse(message)     => message
      case Body.GetClientPermissionsRequest(message)       => message
      case Body.GetClientPermissionsResponse(message)      => message
      case Body.AddPermissionsRequest(message)             => message
      case Body.AddPermissionsResponse(message)            => message
      case Body.RemovePermissionsRequest(message)          => message
      case Body.RemovePermissionsResponse(message)         => message
      case Body.SetPermissionsRequest(message)             => message
      case Body.SetPermissionsResponse(message)            => message
      case Body.GetWorldPermissionsRequest(message)        => message
      case Body.GetWorldPermissionsResponse(message)       => message
      case Body.GetAllUserPermissionsRequest(message)      => message
      case Body.GetAllUserPermissionsResponse(message)     => message
      case Body.GetUserPermissionsRequest(message)         => message
      case Body.GetUserPermissionsResponse(message)        => message
      case Body.GetAllGroupPermissionsRequest(message)     => message
      case Body.GetAllGroupPermissionsResponse(message)    => message
      case Body.GetGroupPermissionsRequest(message)        => message
      case Body.GetGroupPermissionsResponse(message)       => message
      case default                                         => ???
    }

  }

  def toBody(message: GeneratedMessage): Body = {
    message match {
      case message: ErrorMessage                             => Body.Error(message)
      case message: PingMessage                              => Body.Ping(message)
      case message: PongMessage                              => Body.Pong(message)
      case message: HandshakeRequestMessage                  => Body.HandshakeRequest(message)
      case message: HandshakeResponseMessage                 => Body.HandshakeResponse(message)
      case message: PasswordAuthRequestMessage               => Body.PasswordAuthRequest(message)
      case message: TokenAuthRequestMessage                  => Body.TokenAuthRequest(message)
      case message: AnonymousAuthRequestMessage              => Body.AnonymousAuthRequest(message)
      case message: ReconnectTokenAuthRequestMessage         => Body.ReconnectAuthRequest(message)
      case message: AuthenticationResponseMessage            => Body.AuthenticationResponse(message)
      case message: OpenRealtimeModelRequestMessage          => Body.OpenRealTimeModelRequest(message)
      case message: OpenRealtimeModelResponseMessage         => Body.OpenRealTimeModelResponse(message)
      case message: CloseRealtimeModelRequestMessage         => Body.CloseRealTimeModelRequest(message)
      case message: CloseRealTimeModelSuccessMessage         => Body.CloseRealTimeModelResponse(message)
      case message: CreateRealtimeModelRequestMessage        => Body.CreateRealTimeModelRequest(message)
      case message: CreateRealtimeModelSuccessMessage        => Body.CreateRealTimeModelResponse(message)
      case message: DeleteRealtimeModelRequestMessage        => Body.DeleteRealtimeModelRequest(message)
      case message: DeleteRealtimeModelSuccessMessage        => Body.DeleteRealtimeModelResponse(message)
      case message: ModelForceCloseMessage                   => Body.ForceCloseRealTimeModel(message)
      case message: RemoteClientOpenedMessage                => Body.RemoteClientOpenedModel(message)
      case message: RemoteClientClosedMessage                => Body.RemoteClientClosedModel(message)
      case message: AutoCreateModelConfigRequestMessage      => Body.ModelAutoCreateConfigRequest(message)
      case message: AutoCreateModelConfigResponseMessage     => Body.ModelAutoCreateConfigResponse(message)
      case message: RemoteOperationMessage                   => Body.RemoteOperation(message)
      case message: OperationSubmissionMessage               => Body.OperationSubmission(message)
      case message: OperationAcknowledgementMessage          => Body.OperationAck(message)
      case message: PublishReferenceMessage                  => Body.PublishReference(message)
      case message: SetReferenceMessage                      => Body.SetReference(message)
      case message: ClearReferenceMessage                    => Body.ClearReference(message)
      case message: UnpublishReferenceMessage                => Body.UnpublishReference(message)
      case message: RemoteReferencePublishedMessage          => Body.ReferencePublished(message)
      case message: RemoteReferenceSetMessage                => Body.ReferenceSet(message)
      case message: RemoteReferenceClearedMessage            => Body.ReferenceCleared(message)
      case message: RemoteReferenceUnpublishedMessage        => Body.ReferenceUnpublished(message)
      case message: ModelsQueryRequestMessage                => Body.ModelsQueryRequest(message)
      case message: ModelsQueryResponseMessage               => Body.ModelsQueryResponse(message)
      case message: HistoricalDataRequestMessage             => Body.HistoricalDataRequest(message)
      case message: HistoricalDataResponseMessage            => Body.HistoricalDataResponse(message)
      case message: HistoricalOperationRequestMessage        => Body.HistoricalOperationsRequest(message)
      case message: HistoricalOperationsResponseMessage      => Body.HistoricalOperationsResponse(message)
      case message: UserLookUpMessage                        => Body.UserLookUpRequest(message)
      case message: UserSearchMessage                        => Body.UserSearchRequest(message)
      case message: UserListMessage                          => Body.UserListResponse(message)
      case message: UserGroupsRequestMessage                 => Body.UserGroupsRequest(message)
      case message: UserGroupsResponseMessage                => Body.UserGroupsResponse(message)
      case message: UserGroupsForUsersRequestMessage         => Body.UserGroupsForUsersRequest(message)
      case message: UserGroupsForUsersResponseMessage        => Body.UserGroupsForUsersResponse(message)
      case message: ActivityParticipantsRequestMessage       => Body.ActivityParticipantsRequest(message)
      case message: ActivityParticipantsResponseMessage      => Body.ActivityParticipantsResponse(message)
      case message: ActivityJoinMessage                      => Body.ActivityJoinRequest(message)
      case message: ActivityJoinResponseMessage              => Body.ActivityJoinResponse(message)
      case message: ActivityLeaveMessage                     => Body.ActivityLeaveRequest(message)
      case message: ActivitySessionJoinedMessage             => Body.ActivitySessionJoined(message)
      case message: ActivitySessionLeftMessage               => Body.ActivitySessionLeft(message)
      case message: ActivitySetStateMessage                  => Body.ActivityLocalStateSet(message)
      case message: ActivityRemoveStateMessage               => Body.ActivityLocalStateRemoved(message)
      case message: ActivityClearStateMessage                => Body.ActivityLocalStateCleared(message)
      case message: ActivityRemoteStateSetMessage            => Body.ActivityRemoteStateSet(message)
      case message: ActivityRemoteStateRemovedMessage        => Body.ActivityRemoteStateRemoved(message)
      case message: ActivityRemoteStateClearedMessage        => Body.ActivityRemoteStateCleared(message)
      case message: PresenceSetStateMessage                  => Body.PresenceSetState(message)
      case message: PresenceClearStateMessage                => Body.PresenceClearState(message)
      case message: PresenceStateSetMessage                  => Body.PresenceStateSet(message)
      case message: PresenceStateClearedMessage              => Body.PresenceStateCleared(message)
      case message: PresenceRequestMessage                   => Body.PresenceRequest(message)
      case message: PresenceResponseMessage                  => Body.PresenceResponse(message)
      case message: SubscribePresenceRequestMessage          => Body.PresenceSubscribeRequest(message)
      case message: SubscribePresenceResponseMessage         => Body.PresenceSubscribeResponse(message)
      case message: UnsubscribePresenceMessage               => Body.PresenceUnsubscribe(message)
      case message: CreateChatChannelRequestMessage          => Body.CreateChatChannelRequest(message)
      case message: CreateChatChannelResponseMessage         => Body.CreateChatChannelResponse(message)
      case message: RemoveChatChannelRequestMessage          => Body.RemoveChatChannelRequest(message)
      case message: RemoveChatChannelResponseMessage         => Body.RemoveChatChannelResponse(message)
      case message: GetChatChannelsRequestMessage            => Body.GetChatChannelsRequest(message)
      case message: GetChatChannelsResponseMessage           => Body.GetChatChannelsResponse(message)
      case message: ChatChannelsExistsRequestMessage         => Body.ChatChannelExistsRequest(message)
      case message: ChatChannelsExistsResponseMessage        => Body.ChatChannelExistsResponse(message)
      case message: GetDirectChannelsRequestMessage          => Body.GetDirectChatChannelsRequest(message)
      case message: GetDirectChannelsResponseMessage         => Body.GetDirectChatChannelsResponse(message)
      case message: GetJoinedChatChannelsRequestMessage      => Body.GetJoinedChatChannelsRequest(message)
      case message: GetJoinedChatChannelsResponseMessage     => Body.GetJoinedChatChannelsResponse(message)
      case message: JoinChatChannelRequestMessage            => Body.JoinChatChannelRequest(message)
      case message: JoinChatChannelResponseMessage           => Body.JoinChatChannelResponse(message)
      case message: UserJoinedChatChannelMessage             => Body.UserJoinedChatChannel(message)
      case message: LeaveChatChannelRequestMessage           => Body.LeaveChatChannelRequest(message)
      case message: LeaveChatChannelResponseMessage          => Body.LeaveChatChannelResponse(message)
      case message: UserLeftChatChannelMessage               => Body.UserLeftChatChannel(message)
      case message: AddUserToChatChannelRequestMessage       => Body.AddUserToChatChannelRequest(message)
      case message: AddUserToChatChannelResponseMessage      => Body.AddUserToChatChannelResponse(message)
      case message: UserAddedToChatChannelMessage            => Body.UserAddedToChatChannel(message)
      case message: RemoveUserFromChatChannelRequestMessage  => Body.RemoveUserFromChatChannelRequest(message)
      case message: RemoveUserFromChatChannelResponseMessage => Body.RemoveUserFromChatChannelResponse(message)
      case message: UserRemovedFromChatChannelMessage        => Body.UserRemovedFromChatChannel(message)
      case message: SetChatChannelNameRequestMessage         => Body.SetChatChannelNameRequest(message)
      case message: SetChatChannelNameResponseMessage        => Body.SetChatChannelNameResponse(message)
      case message: ChatChannelNameSetMessage                => Body.ChatChannelNameChanged(message)
      case message: SetChatChannelTopicRequestMessage        => Body.SetChatChannelTopicRequest(message)
      case message: SetChatChannelTopicResponseMessage       => Body.SetChatChannelTopicResponse(message)
      case message: ChatChannelTopicSetMessage               => Body.ChatChannelTopicChanged(message)
      case message: MarkChatChannelEventsSeenRequestMessage  => Body.MarkChatChannelEventsSeenRequest(message)
      case message: MarkChatChannelEventsSeenResponseMessage => Body.MarkChatChannelEventsSeenResponse(message)
      case message: ChatChannelEventsMarkedSeenMessage       => Body.ChatChannelEventsMarkedSeen(message)
      case message: PublishChatRequestMessage                => Body.PublishChatMessageRequest(message)
      case message: PublishChatResponseMessage               => Body.PublishChatMessageResponse(message)
      case message: RemoteChatMessageMessage                 => Body.RemoteChatMessage(message)
      case message: ChatChannelHistoryRequestMessage         => Body.GetChatChannelHistoryRequest(message)
      case message: ChatChannelHistoryResponseMessage        => Body.GetChatChannelHistoryResponse(message)
      case message: GetClientPermissionsRequestMessage       => Body.GetClientPermissionsRequest(message)
      case message: GetClientPermissionsReponseMessage       => Body.GetClientPermissionsResponse(message)
      case message: AddPermissionsRequestMessage             => Body.AddPermissionsRequest(message)
      case message: AddPermissionsReponseMessage             => Body.AddPermissionsResponse(message)
      case message: RemovePermissionsRequestMessage          => Body.RemovePermissionsRequest(message)
      case message: RemovePermissionsReponseMessage          => Body.RemovePermissionsResponse(message)
      case message: SetPermissionsRequestMessage             => Body.SetPermissionsRequest(message)
      case message: SetPermissionsReponseMessage             => Body.SetPermissionsResponse(message)
      case message: GetWorldPermissionsRequestMessage        => Body.GetWorldPermissionsRequest(message)
      case message: GetWorldPermissionsResponseMessage       => Body.GetWorldPermissionsResponse(message)
      case message: GetAllUserPermissionsRequestMessage      => Body.GetAllUserPermissionsRequest(message)
      case message: GetAllUserPermissionsReponseMessage      => Body.GetAllUserPermissionsResponse(message)
      case message: GetUserPermissionsRequestMessage         => Body.GetUserPermissionsRequest(message)
      case message: GetUserPermissionsReponseMessage         => Body.GetUserPermissionsResponse(message)
      case message: GetAllGroupPermissionsRequestMessage     => Body.GetAllGroupPermissionsRequest(message)
      case message: GetAllGroupPermissionsReponseMessage     => Body.GetAllGroupPermissionsResponse(message)
      case message: GetGroupPermissionsRequestMessage        => Body.GetGroupPermissionsRequest(message)
      case message: GetGroupPermissionsReponseMessage        => Body.GetGroupPermissionsResponse(message)
      case default                                           => ???
    }
  }

  private[this] def onReply(message: GeneratedMessage with Response, responseId: Int): Unit = {

    requests.synchronized({
      requests.remove(responseId) match {
        case Some(record) => {
          record.future.cancel()
          message match {
            case ErrorMessage(code, message, details) =>
              record.promise.failure(new ClientErrorResponseException(code, message))
            case _ =>
              // There should be no type on a reply message if it is a successful
              // response.
              record.promise.success(message)
          }
        }
        case None =>
        // This can happen when a reply came for a timed out response.
        // TODO should we log this?
      }
    })
  }

  private[this] def onPing(): Unit = {
    sendMessage(convergence.protocol.message.MessageEnvelope().withPong(PongMessage()))
  }

  private[this] def handleHeartbeat: PartialFunction[HeartbeatEvent, Unit] = {
    case PingRequest =>
      sendMessage(convergence.protocol.message.MessageEnvelope().withPing(PingMessage()))
    case PongTimeout =>
      clientActor ! PongTimeout
  }

  class ReplyCallbackImpl(reqId: Int) extends ReplyCallback {
    def reply(message: GeneratedMessage with Response): Unit = {
      sendMessage(MessageEnvelope(None, Some(reqId), toBody(message)))
    }

    def unknownError(): Unit = {
      unexpectedError("An unkown error has occured")
    }

    def unexpectedError(message: String): Unit = {
      expectedError("unknown", message)
    }

    def expectedError(code: String, message: String): Unit = {
      expectedError(code, message, Map[String, String]())
    }

    def expectedError(code: String, message: String, details: Map[String, String]): Unit = {
      val errorMessage = ErrorMessage(code, message, details)

      val envelope = MessageEnvelope(
        None,
        Some(reqId),
        Body.Error(errorMessage))

      sendMessage(envelope)
    }
  }
}

trait ReplyCallback {
  def reply(message: Response): Unit
  def unknownError(): Unit
  def unexpectedError(message: String): Unit
  def expectedError(code: String, message: String): Unit
  def expectedError(code: String, message: String, details: Map[String, Any]): Unit

}

case class RequestRecord(id: Long, promise: Promise[GeneratedMessage with Response], future: Cancellable)
