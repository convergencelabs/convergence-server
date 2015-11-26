package com.convergencelabs.server.schema

import scala.io.Source._
import scala.language.reflectiveCalls
import org.json4s.jackson.Serialization.read
import org.json4s.jackson.Serialization.write
import org.rogach.scallop._
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.command.script.OCommandScript
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseExport
import org.json4s.Formats
import org.json4s.Serialization
import org.json4s.DefaultFormats
import java.io.File
import grizzled.slf4j.Logging

private object Conf {
  def apply(arguments: Seq[String]): Conf = new Conf(arguments)
}

private class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Database Builder 0.1.0 (c) 2015 Convergence Labs")
  banner("Usage: -s files -o filename -m file [-v]")

  val schemaFiles = opt[List[String]](
    argName = "schema-files",
    descr = "A comma separated list of osql schema scripts",
    required = false,
    default = Some(List()))

  val outputFile = opt[String](
    argName = "output",
    descr = "The OrientDB Merge file to output to",
    required = false)

  val verbose = opt[Boolean](
    argName = "verbose",
    descr = "Increase the output verbosity of the tool",
    required = false,
    default = Some(false))

  val manifest = opt[String](
    argName = "manifest",
    descr = "A manifest to run",
    required = false,
    default = None)

  conflicts(manifest, List(schemaFiles, outputFile))
  codependent(schemaFiles, outputFile)
}

object OrientDatabaseBuilder {
  def main(args: Array[String]): Unit = {
    val conf = Conf(args)

    val schemaFiles = conf.schemaFiles.get
    val outputFile = conf.outputFile.get
    val manifestFile = conf.manifest.get
    val verbose = conf.verbose.get.getOrElse(false)

    val builder = new OrientDatabaseBuilder(manifestFile, schemaFiles, outputFile, verbose)
    builder.buildSchema()
  }
}

class OrientDatabaseBuilder(
    private[this] val manifestFile: Option[String],
    private[this] val schemaFiles: Option[List[String]],
    private[this] val outputFile: Option[String],
    private[this] val verbose: Boolean) extends Logging {

  private[this] val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())

  def buildSchema(): Unit = {
    db.create()
    val output = (manifestFile, schemaFiles, outputFile) match {
      case (Some(manifest), None, None) => buildFromManifest(manifest)
      case (None, Some(schema), Some(output)) => buildFromInputOutput(schema, output)
      case _ => ???
    }
    db.drop()
  }

  private[this] def exportDatabase(outputFile: String): Unit = {
    val export = new ODatabaseExport(db, outputFile, new OutputListener(verbose))
    export.exportDatabase()
    export.close()
  }

  private[this] def buildFromInputOutput(schemaFiles: List[String], outputFile: String): Unit = {
    schemaFiles.foreach { filename =>
      processessScript(filename)
    }

    exportDatabase(outputFile)
  }

  private[this] def buildFromManifest(manifestFilePath: String): Unit = {
    val manifestFile = new File(manifestFilePath)
    val manifestData = fromFile(manifestFile).mkString
    implicit val f = DefaultFormats
    val manifest = read[DatabaseBuilderConfig](manifestData)
    processManifestScripts(manifest.schemaScripts, manifestFile.getParent)
    processManifestScripts(manifest.dataScripts, manifestFile.getParent)

    exportDatabase(manifest.outputFile)
  }

  private[this] def processManifestScripts(scriptFiles: Option[List[String]], basePath: String): Unit = {
    scriptFiles match {
      case Some(scripts) => scripts.foreach { script =>
        val potentialFile = new File(script)
        val scriptFile = if (potentialFile.isAbsolute()) {
          potentialFile
        } else {
          new File(basePath, script)
        }
        processessScript(scriptFile.getAbsolutePath)
      }
      case None =>
    }
  }

  private[this] def processessScript(filename: String): Unit = {
    println(filename)

    val source = fromFile(filename)
    try {
      val scriptLines = source.getLines().filter { line =>
        (!line.trim().startsWith("#") && line.trim().size > 0)
      }.mkString("\n")
      
      val semiLines = scriptLines.split(";").map {
        line => line.replace('\n', ' ')
      }

      db.command(new OCommandScript(semiLines.mkString("\n"))).execute()
      db.commit()
    } finally {
      source.close()
    }
  }

}

private class OutputListener(verbose: Boolean) extends OCommandOutputListener() {
  def onMessage(text: String): Unit = {
    if (verbose) {
      println(text)
    }
  }
}