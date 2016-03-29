package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector
import com.convergencelabs.server.util.BiMap
import com.convergencelabs.server.frontend.realtime.model.OperationType
import com.convergencelabs.server.frontend.realtime.data._

object MessageSerializer {

  private[this] val operationSerializer = new TypeMapSerializer[OperationData]("t", Map(
    OperationType.Compound -> classOf[CompoundOperationData],
    OperationType.StringInsert -> classOf[StringInsertOperationData],
    OperationType.StringRemove -> classOf[StringRemoveOperationData],
    OperationType.StringValue -> classOf[StringSetOperationData],

    OperationType.ArrayInsert -> classOf[ArrayInsertOperationData],
    OperationType.ArrayRemove -> classOf[ArrayRemoveOperationData],
    OperationType.ArraySet -> classOf[ArrayReplaceOperationData],
    OperationType.ArrayReorder -> classOf[ArrayMoveOperationData],
    OperationType.ArrayValue -> classOf[ArraySetOperationData],

    OperationType.ObjectAdd -> classOf[ObjectAddPropertyOperationData],
    OperationType.ObjectSet -> classOf[ObjectSetPropertyOperationData],
    OperationType.ObjectRemove -> classOf[ObjectRemovePropertyOperationData],
    OperationType.ObjectValue -> classOf[ObjectSetOperationData],

    OperationType.NumberAdd -> classOf[NumberAddOperationData],
    OperationType.NumberValue -> classOf[NumberSetOperationData],

    OperationType.BooleanValue -> classOf[BooleanSetOperationData]),
    DefaultFormats.withTypeHintFieldName("?") + DataValueTypeHints + DataValueFieldSerializer)

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

    MessageType.OpenRealTimeModelRequest -> classOf[OpenRealtimeModelRequestMessage],
    MessageType.OpenRealTimeModelResponse -> classOf[OpenRealtimeModelResponseMessage],

    MessageType.CloseRealTimeModelRequest -> classOf[CloseRealtimeModelRequestMessage],
    MessageType.CloseRealTimeModelResponse -> classOf[CloseRealTimeModelSuccessMessage],

    MessageType.DeleteRealtimeModelRequest -> classOf[DeleteRealtimeModelRequestMessage],

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
    MessageType.UserListResponse -> classOf[UserListMessage]),

    DefaultFormats.withTypeHintFieldName("?") + operationSerializer + DataValueTypeHints + DataValueFieldSerializer)

  private[this] implicit val formats = DefaultFormats + incomingMessageSerializer

  def writeJson(a: MessageEnvelope): String = {
    write(a)
  }

  def readJson[A](json: String)(implicit mf: Manifest[A]): A = {
    read(json)
  }
}
