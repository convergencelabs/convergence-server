package com.convergencelabs.server.frontend.realtime

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector

import com.convergencelabs.server.util.BiMap

object MessageSerializer {
  private[this] val operationSerializer = new TypeMapSerializer[OperationData]("t", Map(
    "C" -> classOf[CompoundOperationData],
    "SI" -> classOf[StringInsertOperationData],
    "SR" -> classOf[StringRemoveOperationData],
    "SS" -> classOf[StringSetOperationData],

    "AI" -> classOf[ArrayInsertOperationData],
    "AR" -> classOf[ArrayRemoveOperationData],
    "AP" -> classOf[ArrayReplaceOperationData],
    "AM" -> classOf[ArrayMoveOperationData],
    "AS" -> classOf[ArraySetOperationData],

    "OA" -> classOf[ObjectAddPropertyOperationData],
    "OP" -> classOf[ObjectSetPropertyOperationData],
    "OR" -> classOf[ObjectRemovePropertyOperationData],
    "OS" -> classOf[ObjectSetOperationData],

    "NA" -> classOf[NumberAddOperationData],
    "NS" -> classOf[NumberSetOperationData],

    "BS" -> classOf[BooleanSetOperationData]))

  private[this] implicit val formats = DefaultFormats + operationSerializer

  def writeJson[A <: AnyRef](a: A): String = {
    write(a)
  }

  def readJson[A](json: String)(implicit mf: Manifest[A]): A = {
    read(json)
  }

  def extractBody(envelope: MessageEnvelope): ProtocolMessage = {
    val t = envelope.`type`.get
    val body = envelope.body.get
    val clazz = IncomingMessages.getValue(t).get
    extractBody(body, clazz).asInstanceOf[ProtocolMessage]
  }

  def extractBody(body: JValue, t: String): ProtocolMessage = {
    val clazz = IncomingMessages.getValue(t).get
    extractBody(body, clazz).asInstanceOf[ProtocolMessage]
  }

  def extractBody[M <: ProtocolMessage](body: JValue, c: Class[M]): M = {
    Extraction.extract(body, Reflector.scalaTypeOf(c)).asInstanceOf[M]
  }

  def decomposeBody(body: Option[ProtocolMessage]): Option[JValue] = {
    body match {
      case None => None
      case Some(b) => Some(Extraction.decompose(b))
    }
  }

  private[this] val IncomingMessages = new BiMap[String, Class[_ <: ProtocolMessage]](
    MessageType.Handshake -> classOf[HandshakeRequestMessage],

    MessageType.AuthPassword -> classOf[PasswordAuthenticationRequestMessage],
    MessageType.AuthToken -> classOf[TokenAuthenticationRequestMessage],

    MessageType.OpenRealtimeModel -> classOf[OpenRealtimeModelRequestMessage],
    MessageType.CloseRealtimeModel -> classOf[CloseRealtimeModelRequestMessage],

    MessageType.ModelDataRequest -> classOf[ModelDataResponseMessage],
    MessageType.OperationSubmission -> classOf[OperationSubmissionMessage])

  private[this] val OutgoingMessages = Map[Class[_], String](
    classOf[ModelDataRequestMessage] -> MessageType.ModelDataRequest,
    classOf[OperationAcknowledgementMessage] -> MessageType.OperationAck)

  def typeOfOutgoingMessage(message: Option[OutgoingProtocolMessage]): Option[String] = message match {
    case None => None
    case Some(x) => OutgoingMessages.get(x.getClass)
  }
}
