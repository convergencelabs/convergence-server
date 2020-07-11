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

package com.convergencelabs.convergence.server.backend.db.schema.legacy

import java.io.IOException

import com.convergencelabs.convergence.server.util.serialization.SimpleNamePolymorphicSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats}

import scala.io.Source
import scala.util.{Failure, Success, Try}

final class DeltaManifest(basePath: String, index: DeltaIndex) {

  private[this] val mapper = new ObjectMapper(new YAMLFactory())

  def validateIndex(): Try[Unit] = Try {
    for {
      version <- 1 to index.preReleaseVersion
    } yield {
      val incrementalPath = getIncrementalDeltaPath(version)
      val incrementalDelta = loadDeltaScript(incrementalPath).get
      if (incrementalDelta.delta.version != version) {
        throw new IOException(s"Incremental delta version ${incrementalDelta.delta.version} does not match file name: $incrementalPath")
      }
      validateDeltaHash(incrementalDelta, full = false).get

      val fullPath = getFullDeltaPath(version)
      val fullDelta = loadDeltaScript(fullPath).get
      if (fullDelta.delta.version != version) {
        throw new IOException(s"Full delta version ${fullDelta.delta.version} does not match file name: $fullPath")
      }
      validateDeltaHash(fullDelta, full = true).get
    }
  }

  def maxVersion(preRelease: Boolean): Int = {
    if (preRelease) {
      index.preReleaseVersion
    } else {
      index.releasedVersion
    }
  }

  def maxReleasedVersion(): Int = {
    index.releasedVersion
  }

  def maxPreReleaseVersion(): Int = {
    index.preReleaseVersion
  }

  def getFullDelta(version: Int): Try[DeltaScript] = {
    loadDeltaScript(getFullDeltaPath(version)) flatMap { ds =>
      validateDeltaHash(ds, full = true) map { _ => ds }
    }
  }

  def getIncrementalDelta(version: Int): Try[DeltaScript] = {
    loadDeltaScript(getIncrementalDeltaPath(version)) flatMap { ds =>
      validateDeltaHash(ds, full = false) map { _ => ds }
    }
  }

  private[this] def getIncrementalDeltaPath(version: Int): String = {
    s"${basePath}delta-$version.yaml"
  }

  private[this] def getFullDeltaPath(version: Int): String = {
    s"${basePath}database-$version.yaml"
  }

  private[this] def validateDeltaHash(deltaScript: DeltaScript, full: Boolean): Try[Unit] = Try {
    val deltaText = deltaScript.rawScript
    val version = deltaScript.delta.version
    if (version <= index.releasedVersion) {
      val hashes = index.deltas.getOrElse(version.toString, {
        throw new IllegalStateException(s"Delta $version is missing a hash entry.")
      })

      val (path, hash) = if (full) {
        (getFullDeltaPath(version), hashes.database)
      } else {
        (getIncrementalDeltaPath(version), hashes.delta)
      }

      validateHash(hash, deltaText).recoverWith {
        case cause: Exception =>
          Failure(new IOException(s"Delta failed hash validation: $path", cause))
      }.get
    }
  }

  private[this] def validateHash(expectedHash: String, deltaText: String): Try[Unit] = {
    val hash = sha256(deltaText)
    if (hash != expectedHash) {
      Failure(new IOException(s"delta hash validation failed:\nexpected: $expectedHash\nactual : $hash"))
    } else {
      Success(())
    }
  }

  private[this] def sha256(s: String): String = {
    val m = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    m.map("%02x".format(_)).mkString
  }

  private[this] def loadDeltaScript(path: String): Try[DeltaScript] = {
    getDeltaText(path) match {
      case None =>
        Failure(new IOException(s"Delta not found: $path"))
      case Some(rawText) =>
        Try {
          implicit val formats: Formats = DeltaManifest.Formats
          val jsonNode = mapper.readTree(rawText)
          val jValue = JsonMethods.fromJsonNode(jsonNode)
          val parsed = Extraction.extract[Delta](jValue)
          DeltaScript(rawText, parsed)
        } recover {
          case cause: Exception =>
            throw new IOException(s"Could not parse delta: $path", cause)
        }
    }
  }

  private[this] def getDeltaText(path: String): Option[String] = {
    Option(getClass.getResourceAsStream(path)) map { in =>
      Source.fromInputStream(in).mkString
    }
  }
}


object DeltaManifest {
  val Formats: Formats = DefaultFormats +
    SimpleNamePolymorphicSerializer[DeltaAction]("action", List(
      classOf[CreateClass],
      classOf[AlterClass],
      classOf[DropClass],
      classOf[AddProperty],
      classOf[AlterProperty],
      classOf[DropProperty],
      classOf[CreateIndex],
      classOf[DropIndex],
      classOf[CreateSequence],
      classOf[DropSequence],
      classOf[RunSqlCommand],
      classOf[CreateFunction],
      classOf[AlterFunction],
      classOf[DropFunction])) +
    new EnumNameSerializer(OrientType) +
    new EnumNameSerializer(IndexType) +
    new EnumNameSerializer(SequenceType)
}
