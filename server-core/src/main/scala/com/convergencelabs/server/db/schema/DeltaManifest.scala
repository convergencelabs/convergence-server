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
    for (version <- 1 to index.preReleaseVersion) {
      val incrementalPath = getIncrementalDeltaPath(version)
      val incrementalDelta = getDelta(incrementalPath).get
      if (incrementalDelta.version != version) {
        throw new IOException(s"Incremental delta version ${incrementalDelta.version} does not match file name: ${incrementalPath}")
      }

      validateDeltaHash(version, false).get

      val fulllPath = getIncrementalDeltaPath(version)
      val fullDelta = getDelta(fulllPath).get
      if (fullDelta.version != version) {
        throw new IOException(s"Incremental delta version ${fullDelta.version} does not match file name: ${fulllPath}")
      }
      validateDeltaHash(version, true).get
    }
  }

  def maxReleasedVersion(): Int = {
    index.releasedVersion
  }

  def maxPreReleaseVersion(): Int = {
    index.preReleaseVersion
  }

  def getFullDelta(version: Int): Try[Delta] = {
    validateDeltaHash(version, true) flatMap { _ =>
      getDelta(getFullDeltaPath(version))
    }
  }

  def getIncrementalDelta(version: Int): Try[Delta] = {
    validateDeltaHash(version, false) flatMap { _ =>
      getDelta(getIncrementalDeltaPath(version))
    }
  }

  private[this] def getIncrementalDeltaPath(version: Int): String = {
    s"${basePath}delta-${version}.yaml"
  }

  private[this] def getFullDeltaPath(version: Int): String = {
    s"${basePath}database-${version}.yaml"
  }

  private[this] def validateDeltaHash(version: Int, full: Boolean): Try[Unit] = Try {
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

      val in = getDeltaInputStream(path).getOrElse {
        throw new IOException(s"Delta ${version} is missing: ${path}")
      }

      validateHash(hash, in).recoverWith {
        case cause: Exception =>
          Failure(new IOException(s"Delta failed hash validation: ${path}", cause))
      }.get
    }
  }

  private[this] def validateHash(expectedHash: String, deltaInputStream: InputStream): Try[Unit] = {
    val contents = scala.io.Source.fromInputStream(deltaInputStream).mkString
    val hash = SHA256(contents)
    if (hash != expectedHash) {
      Failure(new IOException(s"delta hash validation failed:\nexpected: ${expectedHash}\nactual : ${hash}"))
    } else {
      Success(())
    }
  }

  private[this] def SHA256(s: String): String = {
    val m = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    m.map("%02x".format(_)).mkString
  }

  private[this] def getDeltaInputStream(path: String): Option[InputStream] = {
    Option(getClass.getResourceAsStream(path))
  }

  private[this] def getDelta(path: String): Try[Delta] = {
    getDeltaInputStream(path) match {
      case None =>
        Failure(new IOException(s"Delta not found: ${path}"))
      case Some(in) =>
        Try {
          implicit val formats = (DeltaManifest.Formats)
          val jsonNode = mapper.readTree(in)
          val jValue = JsonMethods.fromJsonNode(jsonNode)
          Extraction.extract[Delta](jValue)
        } recover {
          case cause: Exception =>
            throw new IOException(s"Could not parse delta: ${path}")
        }
    }
  }
}
