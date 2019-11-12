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

package com.convergencelabs.convergence.server.db.schema

import scala.util.Success
import scala.util.Try

import com.orientechnologies.orient.core.db.document.ODatabaseDocument

import grizzled.slf4j.Logging

class TestingSchemaManager(
  db: ODatabaseDocument,
  deltaCategory: DeltaCategory.Value,
  preRelease: Boolean)
    extends AbstractSchemaManager(db, preRelease)
    with Logging {

  def getCurrentVersion(): Try[Int] = {
    Success(0)
  }

  def recordDeltaSuccess(delta: DeltaScript): Try[Unit] = Try {
  }

  def recordDeltaFailure(delta: DeltaScript, cause: Throwable): Unit = {
  }

  def loadManifest(): Try[DeltaManifest] = {
    DeltaManager.manifest(deltaCategory)
  }
}