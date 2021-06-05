/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.db.schema

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[DatabaseSchemaVersionError], name = "error"),
  new JsonSubTypes.Type(value = classOf[DatabaseSchemaNeedsUpgrade], name = "needs_upgrade"),
  new JsonSubTypes.Type(value = classOf[DatabaseSchemaVersionToHigh], name = "version_to_high"),
  new JsonSubTypes.Type(value = classOf[DatabaseSchemaVersionOk], name = "ok"),
))
sealed trait DatabaseSchemaVersionStatus

case class DatabaseSchemaVersionError() extends DatabaseSchemaVersionStatus

case class DatabaseSchemaNeedsUpgrade() extends DatabaseSchemaVersionStatus

case class DatabaseSchemaVersionToHigh() extends DatabaseSchemaVersionStatus

case class DatabaseSchemaVersionOk() extends DatabaseSchemaVersionStatus