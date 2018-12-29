package com.convergencelabs.server.db.schema

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