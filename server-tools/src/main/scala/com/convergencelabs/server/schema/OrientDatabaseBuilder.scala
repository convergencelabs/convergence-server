package com.convergencelabs.server.schema

import java.io.File

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.io.Source.fromFile
import scala.language.reflectiveCalls

import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.read
import org.rogach.scallop.ScallopConf

import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.command.script.OCommandScript
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseExport
import com.orientechnologies.orient.core.serialization.serializer.OStringSerializerHelper

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
    default = None)

  val outputFile = opt[String](
    argName = "output",
    descr = "The OrientDB Merge file to output to",
    required = false,
    default = None)

  val verbose = toggle(
    name = "verbose",
    default = Some(false),
    descrYes = "Print verbose output")

  val manifest = opt[String](
    argName = "manifest",
    descr = "A manifest to run",
    required = false,
    default = None)

  conflicts(manifest, List(schemaFiles, outputFile))
  requireOne(manifest, schemaFiles)
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
    private[this] val verbose: Boolean) {

  private[this] val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())

  def buildSchema(): Unit = {
    if (verbose) {
      println("Creating temporary in memory database")
    }
    db.create()
    val output = (manifestFile, schemaFiles, outputFile) match {
      case (Some(manifest), None, None) => buildFromManifest(manifest)
      case (None, Some(schema), Some(output)) => buildFromInputOutput(schema, output)
      case _ => ???
    }
    if (verbose) {
      println("Closeing temporary in memory database.")
    }
    db.drop()
    println("Database construction complete.")
  }

  private[this] def exportDatabase(outputFile: String): Unit = {
    println(s"Exporting database to $outputFile.")
    val export = new ODatabaseExport(db, outputFile, new OutputListener(verbose))
    export.exportDatabase()
    export.close()
    println("Export complete.")
  }

  private[this] def buildFromInputOutput(schemaFiles: List[String], outputFile: String): Unit = {
    schemaFiles.foreach { filename =>
      processessScript(filename, false)
    }

    exportDatabase(outputFile)
  }

  private[this] def buildFromManifest(manifestFilePath: String): Unit = {
    println(s"Building database from manifiest file: $manifestFilePath")
    val manifestFile = new File(manifestFilePath)
    val manifestData = fromFile(manifestFile).mkString
    implicit val f = DefaultFormats
    val manifest = read[DatabaseBuilderConfig](manifestData)
    processManifestScripts(manifest.schemaScripts, manifestFile.getParent, false)
    processManifestScripts(manifest.dataScripts, manifestFile.getParent, true)

    println(s"Building database build completed")
    exportDatabase(manifest.outputFile)
  }

  private[this] def processManifestScripts(scriptFiles: Option[List[String]], basePath: String, useTransaction: Boolean): Unit = {
    scriptFiles match {
      case Some(scripts) => scripts.foreach { script =>
        val potentialFile = new File(script)
        val scriptFile = if (potentialFile.isAbsolute()) {
          potentialFile
        } else {
          new File(basePath, script)
        }
        processessScript(scriptFile.getAbsolutePath, useTransaction)
      }
      case None =>
    }
  }

  private[this] def processessScript(filename: String, useTransaction: Boolean): Unit = {
    if (verbose) {
      println(s"Processing script: $filename")
    }

    val source = fromFile(filename)
    try {
      val linesAsString = source.getLines().filter { line =>
        (!line.trim().startsWith("#") && line.trim().size > 0)
      }.mkString("\n")

      val smartSplitLines = OStringSerializerHelper.smartSplit(linesAsString, ';', true)
      val mergedLines = smartSplitLines.asScala.toList.map {
        line => line.replace('\n', ' ')
      }
      if(useTransaction) {
        db.begin()
      }
      val sql = mergedLines.mkString("\n")
      println(sql)
      db.command(new OCommandScript(sql)).execute()
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