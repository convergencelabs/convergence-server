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
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelOperation

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
case class AnonymousAuthRequestMessage(d: Option[String]) extends AuthenticationRequestMessage

case class AuthenticationResponseMessage(s: Boolean, n: Option[String], e: Option[String], p: Option[Map[String, Any]]) extends OutgoingProtocolResponseMessage


///////////////////////////////////////////////////////////////////////////////
// Model Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingModelNormalMessage extends IncomingProtocolNormalMessage
case class OperationSubmissionMessage(r: String, s: Long, v: Long, o: OperationData) extends IncomingModelNormalMessage

sealed trait IncomingModelRequestMessage extends IncomingProtocolRequestMessage
case class OpenRealtimeModelRequestMessage(c: String, m: String, i: Boolean) extends IncomingModelRequestMessage
case class CloseRealtimeModelRequestMessage(r: String) extends IncomingModelRequestMessage
case class CreateRealtimeModelRequestMessage(c: String, m: Option[String], d: ObjectValue) extends IncomingModelRequestMessage
case class DeleteRealtimeModelRequestMessage(c: String, m: String) extends IncomingModelRequestMessage


case class ModelsQueryRequestMessage(q: String) extends IncomingModelRequestMessage

case class ModelDataResponseMessage(d: ObjectValue) extends IncomingProtocolResponseMessage

case class PublishReferenceMessage(r: String, d: Option[String], k: String, c: Int, v: Option[List[Any]], s: Option[Long]) extends IncomingModelNormalMessage
case class UnpublishReferenceMessage(r: String, d: Option[String], k: String) extends IncomingModelNormalMessage
case class SetReferenceMessage(r: String, d: Option[String], k: String, c: Int, v: List[Any], s: Long) extends IncomingModelNormalMessage
case class ClearReferenceMessage(r: String, d: Option[String], k: String) extends IncomingModelNormalMessage

// Outgoing Model Messages
case class OpenRealtimeModelResponseMessage(r: String, p: String, v: Long, c: Long, m: Long, d: OpenModelData) extends OutgoingProtocolResponseMessage
case class OpenModelData(d: ObjectValue, s: Set[String], r: Set[ReferenceData])
case class ReferenceData(s: String, d: Option[String], k: String, c: Int, v: List[Any])

case class CloseRealTimeModelSuccessMessage() extends OutgoingProtocolResponseMessage
case class CreateRealtimeModelSuccessMessage(c: String, m: String) extends OutgoingProtocolResponseMessage
case class DeleteRealtimeModelSuccessMessage() extends OutgoingProtocolResponseMessage

case class ModelsQueryResponseMessage(r: List[ModelResult]) extends OutgoingProtocolResponseMessage
case class ModelResult(l: String, m: String, c: Long, d: Long, v: Long, a: ObjectValue)

case class OperationAcknowledgementMessage(r: String, s: Long, v: Long, p: Long) extends OutgoingProtocolNormalMessage
case class RemoteOperationMessage(r: String, s: String, v: Long, p: Long, o: OperationData) extends OutgoingProtocolNormalMessage

case class RemoteClientClosedMessage(r: String, s: String) extends OutgoingProtocolNormalMessage
case class RemoteClientOpenedMessage(r: String, s: String) extends OutgoingProtocolNormalMessage
case class ModelForceCloseMessage(r: String, s: String) extends OutgoingProtocolNormalMessage

case class ModelDataRequestMessage(c: String, m: String) extends OutgoingProtocolRequestMessage

case class RemoteReferencePublishedMessage(r: String, s: String, d: Option[String], k: String, c: Int, v: Option[List[Any]]) extends OutgoingProtocolNormalMessage
case class RemoteReferenceUnpublishedMessage(r: String, s: String, d: Option[String], k: String) extends OutgoingProtocolNormalMessage
case class RemoteReferenceSetMessage(r: String, s: String, d: Option[String], k: String, c: Int, v: List[Any]) extends OutgoingProtocolNormalMessage
case class RemoteReferenceClearedMessage(r: String, s: String, d: Option[String], k: String) extends OutgoingProtocolNormalMessage


///////////////////////////////////////////////////////////////////////////////
// Historical Model Messages
///////////////////////////////////////////////////////////////////////////////
sealed trait IncomingHistoricalModelRequestMessage extends IncomingProtocolRequestMessage
case class HistoricalDataRequestMessage(c: String, m: String) extends IncomingHistoricalModelRequestMessage
case class HistoricalOperationRequestMessage(c: String, m: String, f: Long, l: Long) extends IncomingHistoricalModelRequestMessage

case class HistoricalDataResponseMessage(d: ObjectValue, v: Long, c: Long, m: Long) extends OutgoingProtocolResponseMessage
case class HistoricalOperationsResponseMessage(o: List[ModelOperationData]) extends OutgoingProtocolResponseMessage


///////////////////////////////////////////////////////////////////////////////
// User Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingUserMessage
case class UserLookUpMessage(f: Int, v: List[String]) extends IncomingProtocolRequestMessage with IncomingUserMessage
case class UserSearchMessage(f: List[Int], v: String, o: Option[Int], l: Option[Int], r: Option[Int], s: Option[Int])
  extends IncomingProtocolRequestMessage with IncomingUserMessage

case class UserListMessage(u: List[DomainUserData]) extends OutgoingProtocolResponseMessage
case class DomainUserData(t: String, n: String, f: Option[String], l: Option[String], d: Option[String], e: Option[String])

///////////////////////////////////////////////////////////////////////////////
// Activity Messages
///////////////////////////////////////////////////////////////////////////////

sealed trait IncomingActivityMessage
sealed trait IncomingActivityRequestMessage extends IncomingActivityMessage with IncomingProtocolRequestMessage
case class ActivityParticipantsRequestMessage(i: String) extends IncomingActivityRequestMessage
case class ActivityJoinMessage(i: String, s: Map[String, Any]) extends IncomingActivityRequestMessage

sealed trait IncomingActivityNormalMessage extends IncomingActivityMessage
case class ActivityLeaveMessage(i: String) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivitySetStateMessage(i: String, v: Map[String, Any]) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivityRemoveStateMessage(i: String, k: List[String]) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage
case class ActivityClearStateMessage(i: String) extends IncomingProtocolNormalMessage with IncomingActivityNormalMessage


case class ActivityJoinResponseMessage(s: Map[String, Map[String, Any]]) extends OutgoingProtocolResponseMessage
case class ActivityParticipantsResponseMessage(s: Map[String, Map[String, Any]]) extends OutgoingProtocolResponseMessage


case class ActivitySessionJoinedMessage(i: String, s: String, v: Map[String, Any]) extends OutgoingProtocolNormalMessage
case class ActivitySessionLeftMessage(i: String, s: String) extends OutgoingProtocolNormalMessage

case class ActivityRemoteStateSetMessage(i: String, s: String, v: Map[String, Any]) extends OutgoingProtocolNormalMessage
case class ActivityRemoteStateRemovedMessage(i: String, s: String, k: List[String]) extends OutgoingProtocolNormalMessage
case class ActivityRemoteStateClearedMessage(i: String, s: String) extends OutgoingProtocolNormalMessage

sealed trait IncomingPresenceMessage
sealed trait IncomingPresenceRequestMessage extends IncomingPresenceMessage with IncomingProtocolRequestMessage
case class PresenceRequestMessage(u: List[String]) extends IncomingPresenceRequestMessage
case class SubscribePresenceRequestMessage(u: List[String]) extends IncomingPresenceRequestMessage

case class PresenceResponseMessage(p: List[UserPresence]) extends OutgoingProtocolResponseMessage
case class SubscribePresenceResponseMessage(p: List[UserPresence]) extends OutgoingProtocolResponseMessage

sealed trait IncomingPresenceNormalMessage extends IncomingPresenceMessage with IncomingProtocolNormalMessage
case class PresenceSetStateMessage(s: Map[String, Any]) extends IncomingPresenceNormalMessage
case class PresenceRemoveStateMessage(k: List[String]) extends IncomingPresenceNormalMessage
case class PresenceClearStateMessage() extends IncomingPresenceNormalMessage
case class UnsubscribePresenceMessage(u: String) extends IncomingPresenceNormalMessage

case class PresenceStateSetMessage(u: String, s: Map[String, Any]) extends OutgoingProtocolNormalMessage
case class PresenceStateRemovedMessage(u: String, k: List[String]) extends OutgoingProtocolNormalMessage
case class PresenceStateClearedMessage(u: String) extends OutgoingProtocolNormalMessage
case class PresenceAvailabilityChangedMessage(u: String, a: Boolean) extends OutgoingProtocolNormalMessage

sealed trait IncomingChatMessage
sealed trait IncomingChatRequestMessage extends IncomingChatMessage with IncomingProtocolRequestMessage
case class JoinChatRoomRequestMessage(r: String) extends IncomingChatRequestMessage

case class JoinChatRoomResponseMessage(p: List[String], c: Long, l: Long) extends OutgoingProtocolResponseMessage

sealed trait IncomingChatNormalMessage extends IncomingChatMessage with IncomingProtocolNormalMessage
case class LeftChatRoomMessage(r: String) extends IncomingChatNormalMessage
case class PublishedChatMessage(r: String, m: String) extends IncomingChatNormalMessage

case class UserJoinedRoomMessage(r: String, u: String, s: String, p: Long) extends OutgoingProtocolNormalMessage
case class UserLeftRoomMessage(r: String, u: String, s: String, p: Long) extends OutgoingProtocolNormalMessage
case class UserChatMessage(r: String, u: String, s: String, m: String, p: Long) extends OutgoingProtocolNormalMessage
