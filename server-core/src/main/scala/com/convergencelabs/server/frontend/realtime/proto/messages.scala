package com.convergencelabs.server.frontend.realtime.proto

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue

import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.OpenModelMetaData

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

///////////////////////////////////////////////////////////////////////////////
// Client Messages
///////////////////////////////////////////////////////////////////////////////

case class ErrorMessage(code: String, message: String) extends OutgoingProtocolResponseMessage
case class SuccessMessage() extends OutgoingProtocolResponseMessage

// Handshaking
case class HandshakeRequestMessage(
  reconnect: scala.Boolean,
  reconnectToken: Option[String],
  options: Option[ProtocolOptionsData]) extends IncomingProtocolRequestMessage

case class HandshakeResponseMessage(
  success: scala.Boolean,
  error: Option[ErrorData],
  sessionId: Option[String],
  reconnectToken: Option[String]) extends OutgoingProtocolResponseMessage

case class ProtocolOptionsData()
case class ErrorData(code: String, message: String)

// Authentication Messages
sealed trait AuthenticationRequestMessage extends IncomingProtocolRequestMessage
case class PasswordAuthenticationRequestMessage(username: String, password: String) extends AuthenticationRequestMessage
case class TokenAuthenticationRequestMessage(token: String) extends AuthenticationRequestMessage

case class AuthenticationResponseMessage(success: Boolean, username: Option[String]) extends OutgoingProtocolResponseMessage

///////////////////////////////////////////////////////////////////////////////
// Model Messages
///////////////////////////////////////////////////////////////////////////////
case class ModelFqnData(sId: String, mId: String)

sealed trait IncomingModelNormalMessage extends IncomingProtocolNormalMessage
case class OperationSubmissionMessage(rId: String, seq: Long, v: Long, op: OperationData) extends IncomingModelNormalMessage

sealed trait IncomingModelRequestMessage extends IncomingProtocolRequestMessage
case class OpenRealtimeModelRequestMessage(fqn: ModelFqnData) extends IncomingModelRequestMessage
case class CloseRealtimeModelRequestMessage(rId: String) extends IncomingModelRequestMessage
case class CreateRealtimeModelRequestMessage(fqn: ModelFqnData, data: JValue) extends IncomingModelRequestMessage
case class DeleteRealtimeModelRequestMessage(fqn: ModelFqnData) extends IncomingModelRequestMessage

case class ModelDataResponseMessage(data: JValue) extends IncomingProtocolResponseMessage

// Outgoing Model Messages
case class OpenRealtimeModelResponseMessage(rId: String, v: Long, created: Long, modified: Long, data: JValue) extends OutgoingProtocolResponseMessage

case class OperationAcknowledgementMessage(rId: String, seq: Long, v: Long) extends OutgoingProtocolNormalMessage
case class RemoteOperationMessage(rId: String, uId: String, sId: String, v: Long, t: Long, op: OperationData) extends OutgoingProtocolNormalMessage

case class RemoteClientClosedMessage(rId: String, uId: String, sId: String) extends OutgoingProtocolNormalMessage
case class RemoteClientOpenedMessage(rId: String, uId: String, sId: String) extends OutgoingProtocolNormalMessage
case class ModelForceCloseMessage(rId: String, reason: String) extends OutgoingProtocolNormalMessage

case class ModelDataRequestMessage(modelFqn: ModelFqnData) extends OutgoingProtocolRequestMessage

//
// Operations
//
sealed trait OperationData

case class CompoundOperationData(ops: List[DiscreteOperationData]) extends OperationData

sealed trait DiscreteOperationData extends OperationData {
  def path: List[Any]
  def noOp: Boolean
}

sealed trait StringOperaitonData extends DiscreteOperationData
case class StringInsertOperationData(path: List[Any], noOp: Boolean, idx: Int, `val`: String) extends StringOperaitonData
case class StringRemoveOperationData(path: List[Any], noOp: Boolean, idx: Int, `val`: String) extends StringOperaitonData
case class StringSetOperationData(path: List[Any], noOp: Boolean, `val`: String) extends StringOperaitonData

sealed trait ArrayOperaitonData extends DiscreteOperationData
case class ArrayInsertOperationData(path: List[Any], noOp: Boolean, idx: Int, newVal: JValue) extends ArrayOperaitonData
case class ArrayRemoveOperationData(path: List[Any], noOp: Boolean, idx: Int) extends ArrayOperaitonData
case class ArrayReplaceOperationData(path: List[Any], noOp: Boolean, idx: Int, newVal: JValue) extends ArrayOperaitonData
case class ArrayMoveOperationData(path: List[Any], noOp: Boolean, fromIdx: Int, toIdx: Int) extends ArrayOperaitonData
case class ArraySetOperationData(path: List[Any], noOp: Boolean, array: JArray) extends ArrayOperaitonData

sealed trait ObjectOperaitonData extends DiscreteOperationData
case class ObjectAddPropertyOperationData(path: List[Any], noOp: Boolean, prop: String, newVal: JValue) extends ObjectOperaitonData
case class ObjectSetPropertyOperationData(path: List[Any], noOp: Boolean, prop: String, newVal: JValue) extends ObjectOperaitonData
case class ObjectRemovePropertyOperationData(path: List[Any], noOp: Boolean, prop: String) extends ObjectOperaitonData
case class ObjectSetOperationData(path: List[Any], noOp: Boolean, obj: JObject) extends ObjectOperaitonData

sealed trait NumberOperaitonData extends DiscreteOperationData
case class NumberAddOperationData(path: List[Any], noOp: Boolean, delta: JNumber) extends NumberOperaitonData
case class NumberSetOperationData(path: List[Any], noOp: Boolean, num: JNumber) extends NumberOperaitonData
