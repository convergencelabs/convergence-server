package com.convergencelabs.server.db.schema

import scala.util.Try
import java.io.InputStream
import scala.util.Failure
import org.json4s.ext.EnumNameSerializer
import com.convergencelabs.server.util.SimpleNamePolymorphicSerializer
import org.json4s.DefaultFormats
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import java.io.IOException
import scala.util.Success
import scala.io.Source

object DeltaManifest {
  val Formats = DefaultFormats +
    SimpleNamePolymorphicSerializer[Change]("action", List(
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
      classOf[RunSQLCommand],
      classOf[CreateFunction],
      classOf[AlterFunction],
      classOf[DropFunction])) +
    new EnumNameSerializer(OrientType) +
    new EnumNameSerializer(IndexType) +
    new EnumNameSerializer(SequenceType)
}

class DeltaManifest(
    private[this] val basePath: String,
    private[this] val index: DeltaIndex) {

  private[this] val mapper = new ObjectMapper(new YAMLFactory())

  def validateIndex(): Try[Unit] = Try {
    for {
      version <- 1 to index.preReleaseVersion
    } yield {
      val incrementalPath = getIncrementalDeltaPath(version)
      val incrementalDelta = loadDeltaScript(incrementalPath).get
      if (incrementalDelta.delta.version != version) {
        throw new IOException(s"Incremental delta version ${incrementalDelta.delta.version} does not match file name: ${incrementalPath}")
      }
      validateDeltaHash(incrementalDelta, false).get

      val fulllPath = getFullDeltaPath(version)
      val fullDelta = loadDeltaScript(fulllPath).get
      if (fullDelta.delta.version != version) {
        throw new IOException(s"Full delta version ${fullDelta.delta.version} does not match file name: ${fulllPath}")
      }
      validateDeltaHash(fullDelta, true).get
    }
  }

  def maxVersion(preRelease: Boolean): Int = {
    preRelease match {
      case true =>
        index.preReleaseVersion
      case false =>
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
      validateDeltaHash(ds, true) map { _ => ds }
    }
  }

  def getIncrementalDelta(version: Int): Try[DeltaScript] = {
    loadDeltaScript(getIncrementalDeltaPath(version)) flatMap { ds =>
      validateDeltaHash(ds, false) map { _ => ds }
    }
  }

  private[this] def getIncrementalDeltaPath(version: Int): String = {
    s"${basePath}delta-${version}.yaml"
  }

  private[this] def getFullDeltaPath(version: Int): String = {
    s"${basePath}database-${version}.yaml"
  }

  private[this] def validateDeltaHash(deltaScript: DeltaScript, full: Boolean): Try[Unit] = Try {
    val deltaText = deltaScript.rawScript
    val version = deltaScript.delta.version
    if (version <= index.releasedVersion) {
      val hashes = index.deltas.get(version.toString) getOrElse {
        throw new IllegalStateException(s"Delta ${version} is missing a hash entry.")
      }

      val (path, hash) = full match {
        case true =>
          (getFullDeltaPath(version), hashes.database)
        case false =>
          (getIncrementalDeltaPath(version), hashes.delta)
      }

      validateHash(hash, deltaText).recoverWith {
        case cause: Exception =>
          Failure(new IOException(s"Delta failed hash validation: ${path}", cause))
      }.get
    }
  }

  private[this] def validateHash(expectedHash: String, deltaText: String): Try[Unit] = {
    //    val hash = sha256(deltaText)
    //    if (hash != expectedHash) {
    //      Failure(new IOException(s"delta hash validation failed:\nexpected: ${expectedHash}\nactual : ${hash}"))
    //    } else {
    Success(())
    //    }
  }

  private[this] def sha256(s: String): String = {
    val m = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    m.map("%02x".format(_)).mkString
  }

  private[this] def loadDeltaScript(path: String): Try[DeltaScript] = {
    getDeltaText(path) match {
      case None =>
        Failure(new IOException(s"Delta not found: ${path}"))
      case Some(rawText) =>
        Try {
          implicit val formats = (DeltaManifest.Formats)
          val jsonNode = mapper.readTree(rawText)
          val jValue = JsonMethods.fromJsonNode(jsonNode)
          val parsed = Extraction.extract[Delta](jValue)
          DeltaScript(rawText, parsed)
        } recover {
          case cause: Exception =>
            throw new IOException(s"Could not parse delta: ${path}")
        }
    }
  }

  private[this] def getDeltaText(path: String): Option[String] = {
    Option(getClass.getResourceAsStream(path)) map { in =>
      Source.fromInputStream(in).mkString
    }
  }
}
