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

package com.convergencelabs.convergence.server.backend.db.schema

import java.io.FileNotFoundException

import com.convergencelabs.convergence.server.backend.db.schema.DeltaManager.{DeltaBasePath, IndexFileName}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Extraction}

import scala.util.Try

final class DeltaManager(alternateBasePath: Option[String]) {

  private[this] val mapper = new ObjectMapper(new YAMLFactory())
  private[this] implicit val format: DefaultFormats.type = DefaultFormats
  private[this] val deltaBasePath = alternateBasePath getOrElse DeltaBasePath

  def manifest(deltaCategory: DeltaCategory.Value): Try[DeltaManifest] = {
    val deltaPath = s"$deltaBasePath$deltaCategory/"
    val indexPath = s"$deltaPath$IndexFileName"
    loadDeltaIndex(indexPath) map { index =>
      new DeltaManifest(deltaPath, index)
    }
  }

  private[this] def loadDeltaIndex(indexPath: String): Try[DeltaIndex] = Try {
    val in = getClass.getResourceAsStream(indexPath)
    Option(in) match {
      case Some(stream) =>
        val jsonNode = mapper.readTree(stream)
        val jValue = JsonMethods.fromJsonNode(jsonNode)
        Extraction.extract[DeltaIndex](jValue)
      case None =>
        throw new FileNotFoundException(indexPath)
    }
  }
}

object DeltaManager {
  val DeltaBasePath = "/com/convergencelabs/convergence/server/db/schema/"
  val IndexFileName = "index.yaml"

  def convergenceManifest(): Try[DeltaManifest] = {
    new DeltaManager(None).manifest(DeltaCategory.Convergence)
  }

  def domainManifest(): Try[DeltaManifest] = {
    new DeltaManager(None).manifest(DeltaCategory.Domain)
  }

  def manifest(category: DeltaCategory.Value): Try[DeltaManifest] = {
    category match {
      case DeltaCategory.Convergence =>
        DeltaManager.convergenceManifest()
      case DeltaCategory.Domain =>
        DeltaManager.domainManifest()
    }
  }
}

