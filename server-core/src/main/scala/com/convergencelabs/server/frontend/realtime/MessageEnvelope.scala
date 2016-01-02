package com.convergencelabs.server.frontend.realtime

import scala.util.Try
import org.json4s.JsonAST.JValue

case class MessageEnvelope(opCode: String, reqId: Option[Long], `type`: Option[String], body: Option[JValue])

object MessageEnvelope {
  def apply(json: String): Try[MessageEnvelope] = Try(MessageSerializer.readJson[MessageEnvelope](json))
  
  def apply(body: OutgoingProtocolNormalMessage): MessageEnvelope = {
    val t = MessageSerializer.typeOfOutgoingMessage(Some(body))
    val jValue = MessageSerializer.decomposeBody(Some(body))
    MessageEnvelope(OpCode.Normal, None, t, jValue)
  }
  
  def apply(reqId: Long, body: OutgoingProtocolRequestMessage): MessageEnvelope = {
    val t = MessageSerializer.typeOfOutgoingMessage(Some(body))
    val jValue = MessageSerializer.decomposeBody(Some(body))
    MessageEnvelope(OpCode.Request, Some(reqId), t, jValue)
  }
  
  def apply(reqId: Long, body: OutgoingProtocolResponseMessage): MessageEnvelope = {
    val t = MessageSerializer.typeOfOutgoingMessage(Some(body))
    val jValue = MessageSerializer.decomposeBody(Some(body))
    MessageEnvelope(OpCode.Reply, Some(reqId), t, jValue)
  }
}
