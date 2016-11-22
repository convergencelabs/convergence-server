package com.convergencelabs.server.db.data

import java.time.Instant

case class DomainScript(
  config: SetDomainConfig,
  jwtAuthKeys: Option[List[CreateJwtAuthKey]],
  users: Option[List[CreateDomainUser]],
  collections: Option[List[CreateCollection]],
  models: Option[List[CreateModel]])

case class CreateJwtKeyPair(
  publicKey: String,
  privateKey: String)

case class SetDomainConfig(
  anonymousAuth: Boolean,
  adminJwtKey: CreateJwtKeyPair)

case class CreateDomainUser(
  userType: String,
  username: String,
  firstName: Option[String],
  lastName: Option[String],
  displayName: Option[String],
  email: Option[String],
  password: Option[SetPassword])

case class SetPassword(passwordType: String, value: String)

case class CreateJwtAuthKey(
  keyId: String,
  description: Option[String],
  updated: Instant,
  key: String,
  enabled: Boolean)

case class CreateCollection(collectionId: String, name: String, overrideSnapshotConfig: Boolean)

case class CreateModel(
  collectionId: String,
  modelId: String,
  version: Long,
  created: Instant,
  modified: Instant,
  data: CreateObjectValue,
  operations: List[CreateModelOperation],
  snapshots: List[CreateModelSnapshot])

///////////////////////////////////////////////////////////////////////////////
// Data Values
///////////////////////////////////////////////////////////////////////////////
sealed trait CreateDataValue {
  val vId: String
}

case class CreateObjectValue(vId: String, children: Map[String, CreateDataValue]) extends CreateDataValue

case class CreateArrayValue(vId: String, children: List[CreateDataValue]) extends CreateDataValue

case class CreateBooleanValue(vId: String, value: Boolean) extends CreateDataValue

case class CreateDoubleValue(vId: String, value: Double) extends CreateDataValue

case class CreateNullValue(vId: String) extends CreateDataValue

case class CreateStringValue(vId: String, value: String) extends CreateDataValue

case class CreateModelOperation(
  version: Long,
  timestamp: Instant,
  username: String,
  sessionId: String,
  op: CreateOperation)

///////////////////////////////////////////////////////////////////////////////
// Operations
///////////////////////////////////////////////////////////////////////////////
sealed trait CreateOperation

case class CreateCompoundOperation(operations: List[CreateDiscreteOperation]) extends CreateOperation

sealed trait CreateDiscreteOperation extends CreateOperation {
  def vId: String
  def noOp: Boolean
}

sealed trait CreateStringOperation extends CreateDiscreteOperation
case class CreateStringRemoveOperation(vId: String, noOp: Boolean, index: Int, length: Int, oldValue: Option[String]) extends CreateStringOperation
case class CreateStringInsertOperation(vId: String, noOp: Boolean, index: Int, value: String) extends CreateStringOperation
case class CreateStringSetOperation(vId: String, noOp: Boolean, value: String, oldValue: Option[String]) extends CreateStringOperation

sealed trait CreateObjectOperation extends CreateDiscreteOperation
case class CreateObjectSetPropertyOperation(vId: String, noOp: Boolean, property: String, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectAddPropertyOperation(vId: String, noOp: Boolean, property: String, value: CreateDataValue) extends CreateObjectOperation
case class CreateObjectRemovePropertyOperation(vId: String, noOp: Boolean, property: String, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectSetOperation(vId: String, noOp: Boolean, value: Map[String, CreateDataValue], oldValue: Option[Map[String, CreateDataValue]]) extends CreateObjectOperation

sealed trait CreateNumberOperation extends CreateDiscreteOperation
case class CreateNumberDeltaOperation(vId: String, noOp: Boolean, value: Double) extends CreateNumberOperation
case class CreateNumberSetOperation(vId: String, noOp: Boolean, value: Double, oldValue: Option[Double]) extends CreateNumberOperation

sealed trait CreateBooleanOperation extends CreateDiscreteOperation
case class CreateBooleanSetOperation(vId: String, noOp: Boolean, value: Boolean, oldValue: Option[Boolean]) extends CreateBooleanOperation

sealed trait CreateArrayOperation extends CreateDiscreteOperation
case class CreateArrayInsertOperation(vId: String, noOp: Boolean, index: Int, value: CreateDataValue) extends CreateArrayOperation
case class CreateArrayRemoveOperation(vId: String, noOp: Boolean, index: Int, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReplaceOperation(vId: String, noOp: Boolean, index: Int, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReorderOperation(vId: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends CreateArrayOperation
case class CreateArraySetOperation(vId: String, noOp: Boolean, value: List[CreateDataValue], oldValue: Option[List[CreateDataValue]]) extends CreateArrayOperation

case class CreateModelSnapshot(
  version: Long,
  timestamp: Instant,
  data: CreateObjectValue)
