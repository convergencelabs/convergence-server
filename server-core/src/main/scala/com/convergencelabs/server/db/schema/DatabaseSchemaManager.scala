package com.convergencelabs.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.json4s.ShortTypeHints
import java.io.File
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import com.orientechnologies.orient.core.metadata.schema.OClass
import org.json4s.FieldSerializer
import scala.io.Source
import java.io.FileNotFoundException
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import java.io.InputStream
import grizzled.slf4j.Logging
import com.convergencelabs.server.util.SimpleNamePolymorphicSerializer

object DatabaseSchemaManager {

  def installLatest(db: ODatabaseDocumentTx, category: DeltaCategory.Value): Try[Delta] = {
    new DatabaseSchemaManager(db, category, None).installLatest()
  }

  def installLatest(db: ODatabaseDocumentTx, category: DeltaCategory.Value, preRelease: Boolean): Try[Delta] = {
    new DatabaseSchemaManager(db, category, Some(preRelease)).installLatest()
  }

  def installVersion(db: ODatabaseDocumentTx, category: DeltaCategory.Value, version: Int): Try[Delta] = {
    new DatabaseSchemaManager(db, category, None).installVersion(version)
  }

  def installVersion(db: ODatabaseDocumentTx, category: DeltaCategory.Value, version: Int, preRelease: Boolean): Try[Delta] = {
    new DatabaseSchemaManager(db, category, Some(preRelease)).installVersion(version)
  }

  def upgradeToNextVersion(db: ODatabaseDocumentTx, category: DeltaCategory.Value, currentVersion: Int): Try[Delta] = {
    new DatabaseSchemaManager(db, category, None).upgradeToNextVersion(currentVersion)
  }

  def upgradeToNextVersion(db: ODatabaseDocumentTx, category: DeltaCategory.Value, currentVersion: Int, preRelease: Boolean): Try[Delta] = {
    new DatabaseSchemaManager(db, category, Some(preRelease)).upgradeToNextVersion(currentVersion)
  }
}

class DatabaseSchemaManager(
  private[this] val db: ODatabaseDocumentTx,
  private[this] val category: DeltaCategory.Value,
  private[this] val preRelease: Option[Boolean])
    extends Logging {

  private[this] val releaseOnly = preRelease.getOrElse(false)
  private[this] val deltaManager = new DeltaManager(None)

  def installLatest(): Try[Delta] = {
    deltaManager.manifest(category) flatMap { manifest =>
      val version = if (releaseOnly) {
        manifest.maxReleasedVersion()
      } else {
        manifest.maxPreReleaseVersion()
      }
      manifest.getFullDelta(version)
    } flatMap { delta => applyDelta(delta) }
  }

  def installVersion(version: Int): Try[Delta] = {
    deltaManager.manifest(category) flatMap { manifest =>
      if (releaseOnly && version > manifest.maxReleasedVersion() ||
        !releaseOnly && version > manifest.maxPreReleaseVersion()) {
        Failure(new IllegalArgumentException("Cannot install version that is greater than the max version"))
      }
      manifest.getFullDelta(version)
    } flatMap { applyDelta(_)  }
  }

  def upgradeToNextVersion(currentVersion: Int): Try[Delta] = {
    deltaManager.manifest(category) flatMap { manifest =>
      if (releaseOnly && manifest.maxReleasedVersion() <= currentVersion ||
        !releaseOnly && manifest.maxPreReleaseVersion() <= currentVersion) {
        ??? // TODO: Handle error where currentversion is the most recent
      } else {
        manifest.getIncrementalDelta(currentVersion + 1)
      }
    } flatMap { applyDelta(_)  }
  }

  private[this] def applyDelta(delta: Delta): Try[Delta] = {
      val processor = new DatabaseDeltaProcessor(delta, db)
      processor.apply() map { _ => delta }
  }
}