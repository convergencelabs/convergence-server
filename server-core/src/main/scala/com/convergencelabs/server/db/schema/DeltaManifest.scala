package com.convergencelabs.server.db.schema

import scala.util.Try
import java.io.InputStream
import scala.util.Failure

class DeltaManifest(
    private[this] val basePath: String,
    private[this] val index: DeltaIndex) {

  def validate(): Try[Unit] = Try {
    for (deltaNumber <- 1 to index.maxDelta) {
      val in = getInputStream(deltaNumber) getOrElse {
        throw new IllegalStateException(s"Delta ${deltaNumber} is missing.")
      }

      if (deltaNumber <= index.maxReleasedDelta) {
        validateHash(deltaNumber, in)
      }
    }
  }

  def maxDelta(): Int = {
    index.maxDelta
  }

  def maxReleasedDelta(): Int = {
    index.maxReleasedDelta
  }

  def getInputStream(deltaNumber: Int): Option[InputStream] = {
    Option(getClass.getResourceAsStream(getDeltaPath(deltaNumber)))
  }

  def forEach(currentVersion: Int, preRelease: Boolean)(callback: (Int, String, InputStream) => Try[Unit]): Try[Unit] = Try {
    val max = preRelease match {
      case true => index.maxDelta
      case false => index.maxReleasedDelta
    }

    if (currentVersion < max) {
      for (deltaNumber <- currentVersion + 1 to max) {
        val path = getDeltaPath(deltaNumber)
        val in = getInputStream(deltaNumber) getOrElse {
          throw new IllegalStateException(s"Delta ${deltaNumber} is missing.")
        }

        callback(deltaNumber, path, in).get
      }
    }
  }

  private[this] def getDeltaPath(deltaNumber: Int): String = {
    s"${basePath}${deltaNumber}.yaml"
  }

  private[this] def validateHash(deltaNumber: Int, in: InputStream): Unit = {
    val expectedHash = index.deltas.get(deltaNumber.toString) getOrElse {
      throw new IllegalStateException(s"Delta ${deltaNumber} is missing a hash.")
    }

    val contents = scala.io.Source.fromInputStream(in).mkString
    val hash = SHA256(contents)

    if (hash != expectedHash) {
      throw new IllegalStateException(s"Delta ${deltaNumber} failed hash validation.")
    }
  }

  private[this] def SHA256(s: String): String = {
    val m = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    m.map("%02x".format(_)).mkString
  }

}
