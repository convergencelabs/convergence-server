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

  object TypeMapper {
    val requestTypes = new BiMap(
      "handshake" -> classOf[HandshakeRequestMessage]
    )
    
    val outgoingResponseTypes = new BiMap[String, Class[_]](
      "handshake" -> classOf[HandshakeResponseMessage],
      "foo" -> classOf[OpenRealtimeModelResponseMessage]
    )

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

    def extractResponseBody[T <: ProtocolMessage](implicit c: TypeTag[T]): ProtocolMessage = {
      Extraction.extract(body.get, Reflector.scalaTypeOf(c.tpe.getClass)).asInstanceOf[T]
    }

    def extractBody[M <: ProtocolMessage](c: Class[M]): ProtocolMessage = {
      Extraction.extract(body.get, Reflector.scalaTypeOf(c)).asInstanceOf[M]
    }
    
    def extractBody(): ProtocolMessage = {
      `type`.get match {
        case MessageType.Handshake => Extraction.extract[HandshakeRequestMessage](body.get)
        
        case MessageType.AuthPassword => Extraction.extract[PasswordAuthenticationRequestMessage](body.get)
        case MessageType.AuthToken => Extraction.extract[TokenAuthenticationRequestMessage](body.get)
        
        case MessageType.OpenRealtimeModel => Extraction.extract[OpenRealtimeModelRequestMessage](body.get)
        case MessageType.CloseRealtimeModel => Extraction.extract[CloseRealtimeModelRequestMessage](body.get)
      }
    }

    def toJson(): String = write(this)
  }

  object MessageEnvelope {
    def apply(json: String): Try[MessageEnvelope] = Try(read[MessageEnvelope](json))

    def apply(opCode: String, reqId: Option[Long], body: Option[ProtocolMessage]): MessageEnvelope = {
      val t = typeOf(body)
      val json = body match {
        case None => None
        case Some(b) => Some(Extraction.decompose(b))
      }
      MessageEnvelope(opCode, reqId, t, json)
    }

    def typeOf(message: Option[ProtocolMessage]): Option[String] = message match {
      case None => None
      case Some(x) => x match {
        case _: HandshakeRequestMessage => Some(MessageType.Handshake)
        case _: OpenRealtimeModelRequestMessage => Some(MessageType.OpenRealtimeModel)
        case _: CloseRealtimeModelRequestMessage => Some(MessageType.CloseRealtimeModel)
        case _: PasswordAuthenticationRequestMessage => Some(MessageType.AuthPassword)
        case _: TokenAuthenticationRequestMessage => Some(MessageType.AuthToken)
        case _ => None
      }
    }

  }

  object MessageType extends Enumeration {
    val Error = "error"
    val Handshake = "handshake"
    
    val AuthPassword = "authPassword"
    val AuthToken = "authToken"
    
    val OpenRealtimeModel = "openRealtimeModel"
    val CloseRealtimeModel = "handshake"
  }
}