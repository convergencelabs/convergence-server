package com.convergencelabs.server.frontend.realtime

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenModelMetaData
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.PresenceServiceActor.UserPresence

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

case class ErrorMessage(c: String, d: String)
  extends OutgoingProtocolResponseMessage
  with OutgoingProtocolNormalMessage
  with IncomingProtocolNormalMessage
  with IncomingProtocolResponseMessage

// Handshaking
case class HandshakeRequestMessage(r: scala.Boolean, k: Option[String]) extends IncomingProtocolRequestMessage

case class HandshakeResponseMessage(
  s: scala.Boolean, // success
  e: Option[ErrorData], // error
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
case class TokenAuthRequestMessage(k: String) extends AuthenticationRequestMessage

case class AuthenticationResponseMessage(s: Boolean, n: Option[String], e: Option[String]) extends OutgoingProtocolResponseMessage

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
case class CreateRealtimeModelSuccessMessage() extends OutgoingProtocolResponseMessage
case class DeleteRealtimeModelSuccessMessage() extends OutgoingProtocolResponseMessage

case class OperationAcknowledgementMessage(r: String, s: Long, v: Long, p: Long) extends OutgoingProtocolNormalMessage
case class RemoteOperationMessage(r: String, s: String, v: Long, p: Long, o: OperationData) extends OutgoingProtocolNormalMessage

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
case class DomainUserData(n: String, f: Option[String], l: Option[String], e: Option[String])

///////////////////////////////////////////////////////////////////////////////
// Activity Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingActivityMessage
sealed trait IncomingActivityRequestMessage extends IncomingActivityMessage with IncomingProtocolRequestMessage
case class ActivityParticipantsRequestMessage(i: String) extends IncomingActivityRequestMessage

sealed trait IncomingActivityNormalMessage extends IncomingActivityMessage
case class ActivityJoinMessage(i: String) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivityLeaveMessage(i: String) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivitySetStateMessage(i: String, k: String, v: Any) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivityClearStateMessage(i: String, k: String) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage

case class ActivityParticipantsResponseMessage(s: Map[String, Map[String, Any]]) extends OutgoingProtocolResponseMessage


case class ActivitySessionJoinedMessage(i: String, s: String) extends OutgoingProtocolNormalMessage
case class ActivitySessionLeftMessage(i: String, s: String) extends OutgoingProtocolNormalMessage

case class ActivityRemoteStateSetMessage(i: String, s: String, k: String, v: Any) extends OutgoingProtocolNormalMessage
case class ActivityRemoteStateClearedMessage(i: String, s: String, k: String) extends OutgoingProtocolNormalMessage

sealed trait IncomingPresenceMessage
sealed trait IncomingPresenceRequestMessage extends IncomingPresenceMessage with IncomingProtocolRequestMessage
case class PresenceRequestMessage(u: List[String]) extends IncomingPresenceRequestMessage
case class SubscribePresenceRequestMessage(u: String) extends IncomingPresenceRequestMessage

case class PresenceResponseMessage(p: List[UserPresence]) extends OutgoingProtocolResponseMessage
case class SubscribePresenceResponseMessage(p: UserPresence) extends OutgoingProtocolResponseMessage

sealed trait IncomingPresenceNormalMessage extends IncomingPresenceMessage with IncomingProtocolNormalMessage
case class PresenceSetStateMessage(k: String, v: Any) extends IncomingPresenceNormalMessage
case class PresenceClearStateMessage(k: String) extends IncomingPresenceNormalMessage
case class UnsubscribePresenceMessage(u: String) extends IncomingPresenceNormalMessage

case class PresenceStateSetMessage(u: String, k: String, v: Any) extends OutgoingProtocolNormalMessage
case class PresenceStateClearedMessage(u: String, k: String) extends OutgoingProtocolNormalMessage
case class PresenceAvailabilityChangedMessage(u: String, a: Boolean) extends OutgoingProtocolNormalMessage

sealed trait IncomingChatMessage
sealed trait IncomingChatNormalMessage extends IncomingChatMessage with IncomingProtocolNormalMessage
case class JoinedChatRoomMessage(r: String) extends IncomingChatNormalMessage
case class LeftChatRoomMessage(r: String) extends IncomingChatNormalMessage
case class PublishedChatMessage(r: String, m: String) extends IncomingChatNormalMessage

case class UserJoinedRoomMessage(r: String, u: String, s: String, p: Long) extends OutgoingProtocolNormalMessage
case class UserLeftRoomMessage(r: String, u: String, s: String, p: Long) extends OutgoingProtocolNormalMessage
case class UserChatMessage(r: String, u: String, s: String, m: String, p: Long) extends OutgoingProtocolNormalMessage
