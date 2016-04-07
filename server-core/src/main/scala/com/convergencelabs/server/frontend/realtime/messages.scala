package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenModelMetaData
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.model.data.ObjectValue

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

case class ErrorMessage(c: String, d: String) extends OutgoingProtocolResponseMessage with IncomingProtocolNormalMessage
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
case class TokenAuthRequestMessage(t: String) extends AuthenticationRequestMessage

case class AuthenticationResponseMessage(s: Boolean, u: Option[String]) extends OutgoingProtocolResponseMessage

///////////////////////////////////////////////////////////////////////////////
// Model Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingModelNormalMessage extends IncomingProtocolNormalMessage
case class OperationSubmissionMessage(r: String, s: Long, v: Long, o: OperationData) extends IncomingModelNormalMessage

sealed trait IncomingModelRequestMessage extends IncomingProtocolRequestMessage
case class OpenRealtimeModelRequestMessage(c: String, m: String, i: Boolean) extends IncomingModelRequestMessage
case class CloseRealtimeModelRequestMessage(r: String) extends IncomingModelRequestMessage
case class CreateRealtimeModelRequestMessage(c: String, m: String, d: ObjectValue) extends IncomingModelRequestMessage
case class DeleteRealtimeModelRequestMessage(c: String, m: String) extends IncomingModelRequestMessage

case class ModelDataResponseMessage(d: ObjectValue) extends IncomingProtocolResponseMessage

case class PublishReferenceMessage(r: String, d: String, k: String, c: Int) extends IncomingModelNormalMessage
case class UnpublishReferenceMessage(r: String, d: String, k: String) extends IncomingModelNormalMessage
case class SetReferenceMessage(r: String, d: String, k: String, c: Int, v: Any, s: Long) extends IncomingModelNormalMessage
case class ClearReferenceMessage(r: String, d: String, k: String) extends IncomingModelNormalMessage


// Outgoing Model Messages
case class OpenRealtimeModelResponseMessage(r: String, p: String, v: Long, c: Long, m: Long, d: OpenModelData) extends OutgoingProtocolResponseMessage
case class OpenModelData(d: ObjectValue, s: Set[String], r: Set[ReferenceData])
case class ReferenceData(s: String, d: String, k: String, c: Int, v: Option[Any])

case class CloseRealTimeModelSuccessMessage() extends OutgoingProtocolResponseMessage

case class OperationAcknowledgementMessage(r: String, s: Long, v: Long) extends OutgoingProtocolNormalMessage
case class RemoteOperationMessage(r: String, u: String, s: String, v: Long, p: Long, o: OperationData) extends OutgoingProtocolNormalMessage

case class RemoteClientClosedMessage(r: String, s: String) extends OutgoingProtocolNormalMessage
case class RemoteClientOpenedMessage(r: String, s: String) extends OutgoingProtocolNormalMessage
case class ModelForceCloseMessage(r: String, s: String) extends OutgoingProtocolNormalMessage

case class ModelDataRequestMessage(c: String, m: String) extends OutgoingProtocolRequestMessage

case class RemoteReferencePublishedMessage(r: String, s: String, d: String, k: String, c: Int) extends OutgoingProtocolNormalMessage
case class RemoteReferenceUnpublishedMessage(r: String, s: String, d: String, k: String) extends OutgoingProtocolNormalMessage
case class RemoteReferenceSetMessage(r: String, s: String, d: String, k: String, c: Int, v: Any) extends OutgoingProtocolNormalMessage
case class RemoteReferenceClearedMessage(r: String, s: String, d: String, k: String) extends OutgoingProtocolNormalMessage

///////////////////////////////////////////////////////////////////////////////
// User Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingUserMessage
case class UserLookUpMessage(f: Int, v: List[String]) extends IncomingProtocolRequestMessage with IncomingUserMessage
case class UserSearchMessage(f: List[Int], v: String, o: Option[Int], l: Option[Int], r: Option[Int], s: Option[Int])
  extends IncomingProtocolRequestMessage with IncomingUserMessage

case class UserListMessage(u: List[DomainUserData]) extends OutgoingProtocolResponseMessage
case class DomainUserData(i: String, n: String, f: Option[String], l: Option[String], e: Option[String])
