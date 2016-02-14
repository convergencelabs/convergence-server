package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenModelMetaData
import com.convergencelabs.server.ProtocolConfiguration

// scalastyle:off number.of.types

///////////////////////////////////////////////////////////////////////////////
// Base Classes
///////////////////////////////////////////////////////////////////////////////
sealed trait ProtocolMessage

sealed trait IncomingProtocolMessage extends ProtocolMessage
sealed trait IncomingProtocolNormalMessage extends IncomingProtocolMessage
sealed trait IncomingProtocolRequestMessage extends IncomingProtocolMessage
sealed trait IncomingProtocolResponseMessage extends IncomingProtocolMessage

sealed trait OutgoingProtocolMessage extends ProtocolMessage
sealed trait OutgoingProtocolNormalMessage extends OutgoingProtocolMessage
sealed trait OutgoingProtocolRequestMessage extends OutgoingProtocolMessage
sealed trait OutgoingProtocolResponseMessage extends OutgoingProtocolMessage


case class PingMessage() extends ProtocolMessage
case class PongMessage() extends ProtocolMessage


///////////////////////////////////////////////////////////////////////////////
// Client Messages
///////////////////////////////////////////////////////////////////////////////

case class ErrorMessage(c: String, d: String) extends OutgoingProtocolResponseMessage
case class SuccessMessage() extends OutgoingProtocolResponseMessage

// Handshaking
case class HandshakeRequestMessage(r: scala.Boolean, k: Option[String]) extends IncomingProtocolRequestMessage

case class HandshakeResponseMessage(
  s: scala.Boolean, // success
  e: Option[ErrorData], // error
  i: Option[String], // sessionId
  k: Option[String], // token
  r: Option[scala.Boolean], // retryOk
  c: Option[ProtocolConfigData]) extends OutgoingProtocolResponseMessage

case class ProtocolConfigData(
  h: scala.Boolean // heartbeat enabled
  )

case class ErrorData(
  c: String, // code
  d: String // details
  )

// Authentication Messages
sealed trait AuthenticationRequestMessage extends IncomingProtocolRequestMessage
case class PasswordAuthRequestMessage(u: String, p: String) extends AuthenticationRequestMessage
case class TokenAuthRequestMessage(t: String)  extends AuthenticationRequestMessage
case class AuthenticationResponseMessage(s: Boolean, u: Option[String]) extends OutgoingProtocolResponseMessage

///////////////////////////////////////////////////////////////////////////////
// Model Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingModelNormalMessage extends IncomingProtocolNormalMessage
case class OperationSubmissionMessage(r: String, s: Long, v: Long, o: OperationData) extends IncomingModelNormalMessage

sealed trait IncomingModelRequestMessage extends IncomingProtocolRequestMessage
case class OpenRealtimeModelRequestMessage(c: String, m: String, i: Boolean) extends IncomingModelRequestMessage
case class CloseRealtimeModelRequestMessage(r: String) extends IncomingModelRequestMessage
case class CreateRealtimeModelRequestMessage(c: String, m: String, d: JValue) extends IncomingModelRequestMessage
case class DeleteRealtimeModelRequestMessage(c: String, m: String) extends IncomingModelRequestMessage

case class ModelDataResponseMessage(d: JObject) extends IncomingProtocolResponseMessage

// Outgoing Model Messages
case class OpenRealtimeModelResponseMessage(r: String, v: Long, c: Long, m: Long, d: JValue) extends OutgoingProtocolResponseMessage

case class OperationAcknowledgementMessage(r: String, s: Long, v: Long) extends OutgoingProtocolNormalMessage
case class RemoteOperationMessage(r: String, u: String, s: String, v: Long, p: Long, o: OperationData) extends OutgoingProtocolNormalMessage

case class RemoteClientClosedMessage(r: String, u: String, s: String) extends OutgoingProtocolNormalMessage
case class RemoteClientOpenedMessage(r: String, u: String, s: String) extends OutgoingProtocolNormalMessage
case class ModelForceCloseMessage(r: String, s: String) extends OutgoingProtocolNormalMessage

case class ModelDataRequestMessage(c: String, m: String) extends OutgoingProtocolRequestMessage
