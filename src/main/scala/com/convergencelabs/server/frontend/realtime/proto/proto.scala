package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JValue
import scala.util.Try
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{ read, write }
import org.json4s.NoTypeHints
import org.json4s.Extraction
import org.json4s.reflect.Reflector
import com.convergencelabs.server.domain.model.ModelFqn
import scala.beans.BeanProperty

package object proto {

  object OpCode extends Enumeration {
    val Ping    = "ping"
    val Pong    = "pong"
    val Normal  = "norm"
    val Request = "rqst"
    val Reply   = "rply"
  }

  private[proto] implicit val formats = Serialization.formats(NoTypeHints)

  case class MessageEnvelope(opCode: String, reqId: Option[Long], `type`: Option[String], body: Option[JValue]) {
    def extractBody(): ProtocolMessage = {
      `type`.get match {
        case MessageType.Handshake => Extraction.extract[HandshakeRequestMessage](body.get)
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
    
    def typeOf(message: Option[ProtocolMessage]): Option[String] = {
      message match {
        case None => None
        case Some(x) => x match {
          case _: HandshakeRequestMessage => Some(MessageType.Handshake)
        }
      }
    }
  }

  object MessageType extends Enumeration {
    val Error = "error"
    val Handshake = "handshake"
  }

  sealed trait ProtocolMessage
  case class HandshakeRequestMessage(reconnect: scala.Boolean) extends ProtocolMessage
  
  trait RealtimeModelMessage {
    def modelFqn: ModelFqn
  }
  
  case class OpenRealtimeModelRequestMessage(modelFqn: ModelFqn) extends RealtimeModelMessage
  case class CloseRealtimeModelRequestMessage(modelFqn: ModelFqn) extends RealtimeModelMessage
}