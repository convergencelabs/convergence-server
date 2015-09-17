package com.convergencelabs.server.frontend.realtime.proto

import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenMetaData
import scala.concurrent.Promise

// Main class
sealed trait ProtocolMessage

// Client Messages
case class HandshakeRequestMessage(reconnect: scala.Boolean) extends ProtocolMessage


// Model Messages
sealed trait ModelMessage extends ProtocolMessage
case class OpenRealtimeModelRequestMessage(modelFqn: ModelFqn) extends ModelMessage
case class CloseRealtimeModelRequestMessage(modelFqn: ModelFqn) extends ModelMessage
case class OpenRealtimeModelResponseMessage(resourceId: String, modelSessionId: String, metaData: OpenMetaData, modelData: JValue) extends ProtocolMessage
case class OperationSubmission(resourceId: String, modelSessionId: String, operation: Operation) extends ModelMessage

case class Operation()