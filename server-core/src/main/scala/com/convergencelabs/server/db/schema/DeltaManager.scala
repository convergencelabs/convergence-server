package com.convergencelabs.server.db.schema

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import scala.util.Try
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import java.io.FileNotFoundException
import org.json4s.DefaultFormats

import DeltaManager._

object DeltaCategory extends Enumeration {
  val Convergence = Value("convergence")
  val Domain = Value("domain")
}

object DeltaManager {
  val DeltaBasePath = "/com/convergencelabs/server/db/schema/"
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

class DeltaManager(alternateBasePath: Option[String]) {
  
  private[this] val mapper = new ObjectMapper(new YAMLFactory())
  private[this] implicit val format = DefaultFormats
  val deltaBasePath = alternateBasePath getOrElse(DeltaBasePath)
  
  
  def manifest(deltaCategory: DeltaCategory.Value): Try[DeltaManifest] = {
    val deltaPath = s"${deltaBasePath}${deltaCategory}/"
    val indexPath = s"${deltaPath}${IndexFileName}"
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

