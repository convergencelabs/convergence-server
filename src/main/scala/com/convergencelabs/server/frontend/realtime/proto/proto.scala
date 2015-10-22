package com.convergencelabs.server.frontend.realtime

import scala.beans.BeanProperty
import scala.util.Try
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.json4s.reflect.Reflector
import scala.reflect.runtime.universe._

package object proto {

  case class BiMap[K, V](map: Map[K, V]) {
    def this(tuples: (K, V)*) = this(tuples.toMap)
    private val reverseMap = map map (_.swap)
    require(map.size == reverseMap.size, "no 1 to 1 relation")
    def getValue(k: K): V = map(k)
    def getKey(v: V): K = reverseMap(v)
    val domain = map.keys
    val codomain = reverseMap.keys
  }

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
      val clazz = IncomingMessages.getValue(t)
      extractBody(body, clazz).asInstanceOf[ProtocolMessage]
    }
    
    def extractBody(body: JValue, t: String): ProtocolMessage = {
      val clazz = IncomingMessages.getValue(t)
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

    val IncomingMessages = new BiMap(
      MessageType.Handshake -> classOf[HandshakeRequestMessage],

      MessageType.AuthPassword -> classOf[PasswordAuthenticationRequestMessage],
      MessageType.AuthToken -> classOf[TokenAuthenticationRequestMessage],

      MessageType.OpenRealtimeModel -> classOf[OpenRealtimeModelRequestMessage],
      MessageType.CloseRealtimeModel -> classOf[CloseRealtimeModelRequestMessage])

    val OutgoingMessages = Map[Class[_], String](
      classOf[ModelDataRequestMessage] -> MessageType.ModelDataRequest)

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

  private[proto] implicit val formats = Serialization.formats(NoTypeHints)

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
    
    def apply(opCode: String, reqId: Option[Long], t: String, body: Option[ProtocolMessage]): MessageEnvelope = {
      val jValue = MessageSerializer.decomposeBody(body)
      MessageEnvelope(opCode, reqId, Some(t), jValue)
    }
  }
}