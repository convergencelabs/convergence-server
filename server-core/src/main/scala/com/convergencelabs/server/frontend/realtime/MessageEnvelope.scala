package com.convergencelabs.server.frontend.realtime

import scala.util.Try

case class MessageEnvelope(b: ProtocolMessage, q: Option[Long], p: Option[Long]);

object MessageEnvelope {
  def apply(json: String): Try[MessageEnvelope] = Try(MessageSerializer.readJson[MessageEnvelope](json))
}
