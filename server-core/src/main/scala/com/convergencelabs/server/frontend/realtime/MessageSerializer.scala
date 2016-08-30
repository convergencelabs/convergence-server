package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write

import com.convergencelabs.server.frontend.realtime.data.DataValueFieldSerializer
import com.convergencelabs.server.frontend.realtime.data.DataValueTypeHints

object MessageSerializer {

  private[this] val incomingMessageSerializer = new TypeMapSerializer[ProtocolMessage]("t", Map(
    MessageType.Ping -> classOf[PingMessage],
    MessageType.Pong -> classOf[PongMessage],

    MessageType.Error -> classOf[ErrorMessage],

    MessageType.HandshakeRequest -> classOf[HandshakeRequestMessage],
    MessageType.HandshakeResponse -> classOf[HandshakeResponseMessage],

    MessageType.PasswordAuthRequest -> classOf[PasswordAuthRequestMessage],
    MessageType.TokenAuthRequest -> classOf[TokenAuthRequestMessage],
    MessageType.AuthenticationResponse -> classOf[AuthenticationResponseMessage],

    MessageType.CreateRealTimeModelRequest -> classOf[CreateRealtimeModelRequestMessage],
    MessageType.CreateRealTimeModelResponse -> classOf[CreateRealtimeModelSuccessMessage],

    MessageType.OpenRealTimeModelRequest -> classOf[OpenRealtimeModelRequestMessage],
    MessageType.OpenRealTimeModelResponse -> classOf[OpenRealtimeModelResponseMessage],

    MessageType.CloseRealTimeModelRequest -> classOf[CloseRealtimeModelRequestMessage],
    MessageType.CloseRealTimeModelResponse -> classOf[CloseRealTimeModelSuccessMessage],

    MessageType.DeleteRealtimeModelRequest -> classOf[DeleteRealtimeModelRequestMessage],
    MessageType.DeleteRealtimeModelResponse -> classOf[DeleteRealtimeModelSuccessMessage],

    MessageType.ModelDataResponse -> classOf[ModelDataResponseMessage],
    MessageType.ModelDataRequest -> classOf[ModelDataRequestMessage],

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

    MessageType.UserLookUpRequest -> classOf[UserLookUpMessage],
    MessageType.UserSearchRequest -> classOf[UserSearchMessage],
    MessageType.UserListResponse -> classOf[UserListMessage],

    MessageType.ActivityParticipantsRequest -> classOf[ActivityParticipantsRequestMessage],
    MessageType.ActivityParticipantsResponse -> classOf[ActivityParticipantsResponseMessage],
    MessageType.ActivityJoinRequest -> classOf[ActivityJoinMessage],
    MessageType.ActivityLeaveRequest -> classOf[ActivityLeaveMessage],
    MessageType.ActivitySessionJoined -> classOf[ActivitySessionJoinedMessage],
    MessageType.ActivitySessionLeft -> classOf[ActivitySessionLeftMessage],
    MessageType.ActivityLocalStateSet -> classOf[ActivitySetStateMessage],
    MessageType.ActivityLocalStateCleared -> classOf[ActivityClearStateMessage],
    MessageType.ActivityRemoteStateSet -> classOf[ActivityRemoteStateSetMessage],
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
    
    MessageType.JoinRoom -> classOf[JoinedChatRoomMessage],
    MessageType.LeaveRoom -> classOf[LeftChatRoomMessage],
    MessageType.PublishChatMessage -> classOf[PublishedChatMessage],
    MessageType.UserJoinedRoom -> classOf[UserJoinedRoomMessage],
    MessageType.UserLeftRoom -> classOf[UserLeftRoomMessage],
    MessageType.ChatMessagePublished -> classOf[UserChatMessage],
    
    MessageType.ModelsQueryRequest -> classOf[ModelsQueryRequestMessage],
    MessageType.ModelsQueryResponse -> classOf[ModelsQueryResponseMessage]),

    DefaultFormats.withTypeHintFieldName("?") + new OperationSerializer() + DataValueTypeHints + DataValueFieldSerializer)

  private[this] implicit val formats = DefaultFormats + incomingMessageSerializer

  def writeJson(a: MessageEnvelope): String = {
    write(a)
  }

  def readJson[A](json: String)(implicit mf: Manifest[A]): A = {
    read(json)
  }
}
