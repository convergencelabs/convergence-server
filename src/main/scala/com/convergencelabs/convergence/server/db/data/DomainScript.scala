/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.db.data

import java.time.Instant
import com.convergencelabs.convergence.server.domain.DomainUserId

case class DomainScript(
  config: SetDomainConfig,
  jwtAuthKeys: Option[List[CreateJwtAuthKey]],
  users: Option[List[CreateDomainUser]],
  sessions: Option[List[CreateDomainSession]],
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
  disabled: Boolean,
  deleted: Boolean,
  deletedUsername: Option[String],
  password: Option[SetPassword])

case class CreateDomainSession(
  id: String,
  username: String,
  userType: String,
  connected: Instant,
  disconnected: Option[Instant],
  authMethod: String,
  client: String,
  clientVersion: String,
  clientMetaData: String,
  remoteHost: String)

case class SetPassword(passwordType: String, value: String)

case class CreateJwtAuthKey(
  id: String,
  description: Option[String],
  updated: Instant,
  key: String,
  enabled: Boolean)

case class CreateCollection(id: String, name: String, overrideSnapshotConfig: Boolean)

case class CreateModel(
  id: String,
  collection: String,
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

case class CreateDateValue(id: String, value: Instant) extends CreateDataValue

case class CreateModelOperation(
  version: Long,
  timestamp: Instant,
  sessionId: String,
  op: CreateOperation)

///////////////////////////////////////////////////////////////////////////////
// Operations
///////////////////////////////////////////////////////////////////////////////
sealed trait CreateOperation

case class CreateCompoundOperation(operations: List[CreateDiscreteOperation]) extends CreateOperation

sealed trait CreateDiscreteOperation extends CreateOperation {
  def element: String
  def noOp: Boolean
}

sealed trait CreateStringOperation extends CreateDiscreteOperation
case class CreateStringRemoveOperation(element: String, noOp: Boolean, index: Int, length: Int, oldValue: Option[String]) extends CreateStringOperation
case class CreateStringInsertOperation(element: String, noOp: Boolean, index: Int, value: String) extends CreateStringOperation
case class CreateStringSetOperation(element: String, noOp: Boolean, value: String, oldValue: Option[String]) extends CreateStringOperation

sealed trait CreateObjectOperation extends CreateDiscreteOperation
case class CreateObjectSetPropertyOperation(element: String, noOp: Boolean, property: String, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectAddPropertyOperation(element: String, noOp: Boolean, property: String, value: CreateDataValue) extends CreateObjectOperation
case class CreateObjectRemovePropertyOperation(element: String, noOp: Boolean, property: String, oldValue: Option[CreateDataValue]) extends CreateObjectOperation
case class CreateObjectSetOperation(element: String, noOp: Boolean, value: Map[String, CreateDataValue], oldValue: Option[Map[String, CreateDataValue]]) extends CreateObjectOperation

sealed trait CreateNumberOperation extends CreateDiscreteOperation
case class CreateNumberDeltaOperation(element: String, noOp: Boolean, value: Double) extends CreateNumberOperation
case class CreateNumberSetOperation(element: String, noOp: Boolean, value: Double, oldValue: Option[Double]) extends CreateNumberOperation

sealed trait CreateBooleanOperation extends CreateDiscreteOperation
case class CreateBooleanSetOperation(element: String, noOp: Boolean, value: Boolean, oldValue: Option[Boolean]) extends CreateBooleanOperation

sealed trait CreateArrayOperation extends CreateDiscreteOperation
case class CreateArrayInsertOperation(element: String, noOp: Boolean, index: Int, value: CreateDataValue) extends CreateArrayOperation
case class CreateArrayRemoveOperation(element: String, noOp: Boolean, index: Int, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReplaceOperation(element: String, noOp: Boolean, index: Int, value: CreateDataValue, oldValue: Option[CreateDataValue]) extends CreateArrayOperation
case class CreateArrayReorderOperation(element: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends CreateArrayOperation
case class CreateArraySetOperation(element: String, noOp: Boolean, value: List[CreateDataValue], oldValue: Option[List[CreateDataValue]]) extends CreateArrayOperation

sealed trait CreateDateOperation extends CreateDiscreteOperation
case class CreateDateSetOperation(element: String, noOp: Boolean, value: Instant, oldValue: Option[Instant]) extends CreateBooleanOperation

case class CreateModelSnapshot(
  version: Long,
  timestamp: Instant,
  data: CreateObjectValue)
