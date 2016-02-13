package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector
import com.convergencelabs.server.util.BiMap
import com.convergencelabs.server.frontend.realtime.model.OperationType

object MessageSerializer {

  def writeJson(a: MessageEnvelope): String = {
    write(a)
  }

  def readJson[A](json: String)(implicit mf: Manifest[A]): A = {
    read(json)
  }

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

    OperationType.BooleanValue -> classOf[BooleanSetOperationData]))

  private[this] val incomingMessageSerializer = new TypeMapSerializer[ProtocolMessage]("t", Map(
    MessageType.HandshakeRequest -> classOf[HandshakeRequestMessage],

    MessageType.PasswordAuthRequest -> classOf[PasswordAuthRequestMessage],
    MessageType.TokenAuthRequest -> classOf[TokenAuthRequestMessage],

    MessageType.CreateRealTimeModelRequest -> classOf[CreateRealtimeModelRequestMessage],
    MessageType.OpenRealTimeModelRequest -> classOf[OpenRealtimeModelRequestMessage],
    MessageType.CloseRealTimeModelRequest -> classOf[CloseRealtimeModelRequestMessage],
    MessageType.DeleteRealtimeModelRequest -> classOf[DeleteRealtimeModelRequestMessage],

    MessageType.ModelDataResponse -> classOf[ModelDataResponseMessage],
    MessageType.OperationSubmission -> classOf[OperationSubmissionMessage],

    MessageType.ModelDataRequest -> classOf[ModelDataRequestMessage],
    MessageType.OperationAck -> classOf[OperationAcknowledgementMessage],
    MessageType.ForceCloseRealTimeModel -> classOf[ModelForceCloseMessage],
    MessageType.RemoteOperation ->classOf[RemoteOperationMessage]
  ))

  private[this] implicit val formats = DefaultFormats + operationSerializer + incomingMessageSerializer
}
