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
  id: String,
  description: Option[String],
  updated: Instant,
  key: String,
  enabled: Boolean)

case class CreateCollection(id: String, name: String, overrideSnapshotConfig: Boolean)

case class CreateModel(
  collection: String,
  id: String,
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
  val id: String
}

case class CreateObjectValue(id: String, children: Map[String, CreateDataValue]) extends CreateDataValue

case class CreateArrayValue(id: String, children: List[CreateDataValue]) extends CreateDataValue

case class CreateBooleanValue(id: String, value: Boolean) extends CreateDataValue

case class CreateDoubleValue(id: String, value: Double) extends CreateDataValue

case class CreateNullValue(id: String) extends CreateDataValue

case class CreateStringValue(id: String, value: String) extends CreateDataValue

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
  def id: String
  def noOp: Boolean
}

sealed trait CreateStringOperation extends CreateDiscreteOperation
case class CreateStringRemoveOperation(id: String, noOp: Boolean, index: Int, length: Int, oldValue: Option[String]) extends CreateStringOperation
case class CreateStringInsertOperation(id: String, noOp: Boolean, index: Int, value: String) extends CreateStringOperation
case class CreateStringSetOperation(id: String, noOp: Boolean, value: String, oldValue: Option[String]) extends CreateStringOperation

sealed trait CreateObjectOperation extends CreateDiscreteOperation
case class CreateObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: CreateDataValue) extends CreateObjectOperation
case class CreateObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectSetOperation(id: String, noOp: Boolean, value: Map[String, CreateDataValue], oldValue: Option[Map[String, CreateDataValue]]) extends CreateObjectOperation

sealed trait CreateNumberOperation extends CreateDiscreteOperation
case class CreateNumberDeltaOperation(id: String, noOp: Boolean, value: Double) extends CreateNumberOperation
case class CreateNumberSetOperation(id: String, noOp: Boolean, value: Double, oldValue: Option[Double]) extends CreateNumberOperation

sealed trait CreateBooleanOperation extends CreateDiscreteOperation
case class CreateBooleanSetOperation(id: String, noOp: Boolean, value: Boolean, oldValue: Option[Boolean]) extends CreateBooleanOperation

sealed trait CreateArrayOperation extends CreateDiscreteOperation
case class CreateArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: CreateDataValue) extends CreateArrayOperation
case class CreateArrayRemoveOperation(id: String, noOp: Boolean, index: Int, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReorderOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends CreateArrayOperation
case class CreateArraySetOperation(id: String, noOp: Boolean, value: List[CreateDataValue], oldValue: Option[List[CreateDataValue]]) extends CreateArrayOperation

case class CreateModelSnapshot(
  version: Long,
  timestamp: Instant,
  data: CreateObjectValue)
