package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write

import com.convergencelabs.server.frontend.realtime.data.DataValueFieldSerializer
import com.convergencelabs.server.frontend.realtime.data.DataValueTypeHints
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString
import java.time.Instant
import java.text.SimpleDateFormat
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JLong
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JDecimal
import java.util.Date
import java.util.TimeZone

object MessageSerializer {

  val UTC = TimeZone.getTimeZone("UTC")
  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  df.setTimeZone(UTC)

  private[this] val instantSerializer = new CustomSerializer[Instant](formats => ({
    case JString(dateString) =>
      // TODO look into Instant.Parse
      val date = df.parse(dateString)
      Instant.ofEpochMilli(date.getTime)
    case JInt(millis) =>
      Instant.ofEpochMilli(millis.longValue())
    case JLong(millis) =>
      Instant.ofEpochMilli(millis)
    case JDouble(millis) =>
      Instant.ofEpochMilli(millis.longValue())
    case JDecimal(millis) =>
      Instant.ofEpochMilli(millis.longValue())
  }, {
    case x: Instant =>
      JInt(x.toEpochMilli())
  }))

  private[this] val incomingMessageSerializer = new TypeMapSerializer[ProtocolMessage]("t", Map(
    MessageType.Ping -> classOf[PingMessage],
    MessageType.Pong -> classOf[PongMessage],

    MessageType.Error -> classOf[ErrorMessage],

    MessageType.HandshakeRequest -> classOf[HandshakeRequestMessage],
    MessageType.HandshakeResponse -> classOf[HandshakeResponseMessage],

    MessageType.PasswordAuthRequest -> classOf[PasswordAuthRequestMessage],
    MessageType.TokenAuthRequest -> classOf[TokenAuthRequestMessage],
    MessageType.AnonymousAuthRequest -> classOf[AnonymousAuthRequestMessage],
    MessageType.AuthenticationResponse -> classOf[AuthenticationResponseMessage],

    MessageType.CreateRealTimeModelRequest -> classOf[CreateRealtimeModelRequestMessage],
    MessageType.CreateRealTimeModelResponse -> classOf[CreateRealtimeModelSuccessMessage],

    MessageType.OpenRealTimeModelRequest -> classOf[OpenRealtimeModelRequestMessage],
    MessageType.OpenRealTimeModelResponse -> classOf[OpenRealtimeModelResponseMessage],

    MessageType.CloseRealTimeModelRequest -> classOf[CloseRealtimeModelRequestMessage],
    MessageType.CloseRealTimeModelResponse -> classOf[CloseRealTimeModelSuccessMessage],

    MessageType.DeleteRealtimeModelRequest -> classOf[DeleteRealtimeModelRequestMessage],
    MessageType.DeleteRealtimeModelResponse -> classOf[DeleteRealtimeModelSuccessMessage],

    MessageType.ModelAutoCreateConfigResponse -> classOf[AutoCreateModelConfigResponseMessage],
    MessageType.ModelAutoCreateConfigRequest -> classOf[AutoCreateModelConfigRequestMessage],

    MessageType.OperationSubmission -> classOf[OperationSubmissionMessage],
    MessageType.OperationAck -> classOf[OperationAcknowledgementMessage],
    MessageType.RemoteOperation -> classOf[RemoteOperationMessage],

    MessageType.ForceCloseRealTimeModel -> classOf[ModelForceCloseMessage],

    MessageType.RemoteClientOpenedModel -> classOf[RemoteClientOpenedMessage],
    MessageType.RemoteClientClosedModel -> classOf[RemoteClientClosedMessage],

    MessageType.PublishReference -> classOf[PublishReferenceMessage],
    MessageType.UnpublishReference -> classOf[UnpublishReferenceMessage],
    MessageType.SetReference -> classOf[SetReferenceMessage],
    MessageType.ClearReference -> classOf[ClearReferenceMessage],

    MessageType.ReferencePublished -> classOf[RemoteReferencePublishedMessage],
    MessageType.ReferenceUnpublished -> classOf[RemoteReferenceUnpublishedMessage],
    MessageType.ReferenceSet -> classOf[RemoteReferenceSetMessage],
    MessageType.ReferenceCleared -> classOf[RemoteReferenceClearedMessage],

    MessageType.GetModelPermissionsRequest -> classOf[GetModelPermissionsRequestMessage],
    MessageType.GetModelPermissionsResponse -> classOf[GetModelPermissionsResponseMessage],
    MessageType.SetModelPermissionsRequest -> classOf[SetModelPermissionsRequestMessage],
    MessageType.SetModelPermissionsResponse -> classOf[SetModelPermissionsResponseMessage],
    MessageType.ModelPermissionsChanged -> classOf[ModelPermissionsChangedMessage],

    MessageType.ModelsQueryRequest -> classOf[ModelsQueryRequestMessage],
    MessageType.ModelsQueryResponse -> classOf[ModelsQueryResponseMessage],

    MessageType.HistoricalDataRequest -> classOf[HistoricalDataRequestMessage],
    MessageType.HistoricalDataResponse -> classOf[HistoricalDataResponseMessage],
    MessageType.HistoricalOperationsRequest -> classOf[HistoricalOperationRequestMessage],
    MessageType.HistoricalOperationsResponse -> classOf[HistoricalOperationsResponseMessage],

    MessageType.UserLookUpRequest -> classOf[UserLookUpMessage],
    MessageType.UserSearchRequest -> classOf[UserSearchMessage],
    MessageType.UserListResponse -> classOf[UserListMessage],

    MessageType.ActivityParticipantsRequest -> classOf[ActivityParticipantsRequestMessage],
    MessageType.ActivityParticipantsResponse -> classOf[ActivityParticipantsResponseMessage],
    MessageType.ActivityJoinRequest -> classOf[ActivityJoinMessage],
    MessageType.ActivityJoinResponse -> classOf[ActivityJoinResponseMessage],
    MessageType.ActivityLeaveRequest -> classOf[ActivityLeaveMessage],
    MessageType.ActivitySessionJoined -> classOf[ActivitySessionJoinedMessage],
    MessageType.ActivitySessionLeft -> classOf[ActivitySessionLeftMessage],
    MessageType.ActivityLocalStateSet -> classOf[ActivitySetStateMessage],
    MessageType.ActivityLocalStateRemoved -> classOf[ActivityRemoveStateMessage],
    MessageType.ActivityLocalStateCleared -> classOf[ActivityClearStateMessage],
    MessageType.ActivityRemoteStateSet -> classOf[ActivityRemoteStateSetMessage],
    MessageType.ActivityRemoteStateRemoved -> classOf[ActivityRemoteStateRemovedMessage],
    MessageType.ActivityRemoteStateCleared -> classOf[ActivityRemoteStateClearedMessage],

    MessageType.PresenceSetState -> classOf[PresenceSetStateMessage],
    MessageType.PresenceClearState -> classOf[PresenceClearStateMessage],
    MessageType.PresenceStateSet -> classOf[PresenceStateSetMessage],
    MessageType.PresenceStateCleared -> classOf[PresenceStateClearedMessage],
    MessageType.PresenceRequest -> classOf[PresenceRequestMessage],
    MessageType.PresenceResponse -> classOf[PresenceResponseMessage],
    MessageType.PresenceSubscribeRequest -> classOf[SubscribePresenceRequestMessage],
    MessageType.PresenceSubscribeResponse -> classOf[SubscribePresenceResponseMessage],
    MessageType.PresenceUnsubscribe -> classOf[UnsubscribePresenceMessage],

    MessageType.CreateChatChannelRequest -> classOf[CreateChatChannelRequestMessage],
    MessageType.CreateChatChannelResponse -> classOf[CreateChatChannelResponseMessage],
    MessageType.RemoveChatChannelRequest -> classOf[RemoveChatChannelRequestMessage],
    MessageType.RemoveChatChannelResponse -> classOf[RemoveChatChannelResponseMessage],

    MessageType.JoinChatChannelRequest -> classOf[JoinChatChannelRequestMessage],
    MessageType.JoinChatChannelResponse -> classOf[JoinChatChannelResponseMessage],
    MessageType.UserJoinedChatChannel -> classOf[UserJoinedChatChannelMessage],

    MessageType.LeaveChatChannelRequest -> classOf[LeaveChatChannelRequestMessage],
    MessageType.LeaveChatChannelResponse -> classOf[LeaveChatChannelResponseMessage],
    MessageType.UserLeftChatChannel -> classOf[UserLeftChatChannelMessage],

    MessageType.AddUserToChatChannelRequest -> classOf[AddUserToChatChannelRequestMessage],
    MessageType.AddUserToChatChannelResponse -> classOf[AddUserToChatChannelResponseMessage],
    MessageType.UserAddedToChatChannel -> classOf[UserAddedToChatChannelMessage],

    MessageType.RemoveUserFromChatChannelRequest -> classOf[RemoveUserFromChatChannelRequestMessage],
    MessageType.RemoveUserFromChatChannelResponse -> classOf[RemoveUserFromChatChannelResponseMessage],
    MessageType.UserRemovedFromChatChannel -> classOf[UserRemovedFromChatChannelMessage],

    MessageType.PublishChatMessageRequest -> classOf[PublishChatRequestMessage],
    MessageType.PublishChatMessageResponse -> classOf[PublishChatResponseMessage],
    MessageType.RemoteChatMessage -> classOf[RemoteChatMessage],

    MessageType.SetChatChannelNameRequest -> classOf[SetChatChannelNameRequestMessage],
    MessageType.SetChatChannelNameResponse -> classOf[SetChatChannelNameResponseMessage],
    MessageType.ChatChannelNameChanged -> classOf[ChatChannelNameSetMessage],

    MessageType.SetChatChannelTopicRequest -> classOf[SetChatChannelTopicRequestMessage],
    MessageType.SetChatChannelTopicResponse -> classOf[SetChatChannelTopicResponseMessage],
    MessageType.ChatChannelTopicChanged -> classOf[ChatChannelTopicSetMessage],

    MessageType.MarkChatChannelEventsSeenRequest -> classOf[MarkChatChannelEventsSeenRequestMessage],
    MessageType.MarkChatChannelEventsSeenResponse -> classOf[MarkChatChannelEventsSeenResponseMessage],
    MessageType.ChatChannelEventsMarkedSeen -> classOf[ChatChannelEventsMarkedSeenMessage],

    MessageType.GetJoinedChatChannelsRequest -> classOf[GetJoinedChatChannelsRequestMessage],
    MessageType.GetJoinedChatChannelsResponse -> classOf[GetJoinedChatChannelsResponseMessage],

    MessageType.GetChatChannelsRequest -> classOf[GetChatChannelsRequestMessage],
    MessageType.GetChatChannelsResponse -> classOf[GetChatChannelsResponseMessage],

    MessageType.GetDirectChatChannelsRequest -> classOf[GetDirectChannelsRequestMessage],
    MessageType.GetDirectChatChannelsResponse -> classOf[GetDirectChannelsResponseMessage],
    
    MessageType.GetChatChannelHistoryRequest -> classOf[ChatChannelHistoryRequestMessage],
    MessageType.GetChatChannelHistoryResponse -> classOf[ChatChannelHistoryResponseMessage]),

    DefaultFormats.withTypeHintFieldName("?") + new OperationSerializer() + new AppliedOperationSerializer() + DataValueTypeHints + DataValueFieldSerializer + instantSerializer)

  private[this] implicit val formats = DefaultFormats + incomingMessageSerializer

  def writeJson(a: MessageEnvelope): String = {
    write(a)
  }

  def readJson[A](json: String)(implicit mf: Manifest[A]): A = {
    read(json)
  }
}
