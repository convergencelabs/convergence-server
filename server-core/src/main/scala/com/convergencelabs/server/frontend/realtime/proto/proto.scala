package com.convergencelabs.server.frontend.realtime

import scala.beans.BeanProperty
import scala.util.Try
import org.json4s._
import org.json4s.JsonAST.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector
import scala.reflect.runtime.universe._
import org.json4s.ShortTypeHints
import org.json4s.TypeHints
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JField
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

package object proto {

  case class BiMap[K, V](map: Map[K, V]) {
    def this(tuples: (K, V)*) = this(tuples.toMap)
    private val reverseMap = map map (_.swap)
    require(map.size == reverseMap.size, "no 1 to 1 relation")
    def getValue(k: K): Option[V] = map.get(k)
    def getKey(v: V): Option[K] = reverseMap.get(v)
    val domain = map.keys
    val codomain = reverseMap.keys
  }
  
  val operationSerializer = new TypeMapSerializer[OperationData]("t", Map(
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
    "OS" -> classOf[ObjectSetOperationData]
    ))

  private[proto] implicit val formats = DefaultFormats + operationSerializer

  object MessageSerializer {
    object MessageType extends Enumeration {
      val Error = "error"
      val Handshake = "handshake"

      val AuthPassword = "authPassword"
      val AuthToken = "authToken"

      val OpenRealtimeModel = "openRealtimeModel"
      val CloseRealtimeModel = "closeRealtimeModel"

      val ModelDataRequest = "modelData"
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

    val IncomingMessages = new BiMap[String, Class[_ <: ProtocolMessage]](
      MessageType.Handshake -> classOf[HandshakeRequestMessage],

      MessageType.AuthPassword -> classOf[PasswordAuthenticationRequestMessage],
      MessageType.AuthToken -> classOf[TokenAuthenticationRequestMessage],

      MessageType.OpenRealtimeModel -> classOf[OpenRealtimeModelRequestMessage],
      MessageType.CloseRealtimeModel -> classOf[CloseRealtimeModelRequestMessage],

      MessageType.ModelDataRequest -> classOf[ModelDataResponseMessage],
      "opSubmit" -> classOf[OperationSubmissionMessage])

    val OutgoingMessages = Map[Class[_], String](
      classOf[ModelDataRequestMessage] -> MessageType.ModelDataRequest,
      classOf[OperationAcknowledgementMessage] -> "opAck")

    def typeOf(message: Option[ProtocolMessage]): Option[String] = message match {
      case None => None
      case Some(x) => OutgoingMessages.get(x.getClass)
    }
  }

  object OpCode extends Enumeration {
    val Ping = "ping"
    val Pong = "pong"
    val Normal = "norm"
    val Request = "rqst"
    val Reply = "rply"
  }

  // FIXME can we use the message type enum instead for matching?
  case class MessageEnvelope(opCode: String, reqId: Option[Long], `type`: Option[String], body: Option[JValue]) {
    def toJson(): String = write(this)
  }

  object MessageEnvelope {
    def apply(json: String): Try[MessageEnvelope] = Try(read[MessageEnvelope](json))

    def apply(opCode: String, reqId: Option[Long], body: Option[ProtocolMessage]): MessageEnvelope = {
      val t = MessageSerializer.typeOf(body)
      val jValue = MessageSerializer.decomposeBody(body)
      MessageEnvelope(opCode, reqId, t, jValue)
    }

    def apply(opCode: String, reqId: Long, t: String, body: Option[ProtocolMessage]): MessageEnvelope = {
      val jValue = MessageSerializer.decomposeBody(body)
      MessageEnvelope(opCode, Some(reqId), Some(t), jValue)
    }

    def apply(opCode: String, t: String, body: Option[ProtocolMessage]): MessageEnvelope = {
      val jValue = MessageSerializer.decomposeBody(body)
      MessageEnvelope(opCode, None, Some(t), jValue)
    }
  }
}