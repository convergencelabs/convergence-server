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
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.PartialFunction.AndThen
import scala.util.control.NonFatal
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.ext.EnumNameSerializer
import org.json4s.ShortTypeHints
import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.jackson.JsonMethods

private object Conf {
  def apply(arguments: Seq[String]): Conf = new Conf(arguments)
}

private class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Database Builder 0.1.0 (c) 2015 Convergence Labs")
  banner("Usage: -s files -o filename -m file [-v]")

  val sourceDir = opt[String](
    argName = "source",
    descr = "The locatin of the source folder",
    required = false,
    default = Some("src/main/odb"))

  val deltaDir = opt[String](
    argName = "deltas",
    descr = "The relative locatin of the delta dir relative to the root",
    required = false,
    default = Some("deltas"))

  val dataDir = opt[String](
    argName = "data",
    descr = "The relative locatin of the data dir relative to the source",
    required = false,
    default = Some("data"))

  val databaseDir = opt[String](
    argName = "database",
    descr = "The relative locatin of the database dir relative to the source",
    required = false,
    default = Some("database"))

  val targetDir = opt[String](
    argName = "target",
    descr = "The location of the target folder",
    required = false,
    default = Some("target/odb/"))

  val verbose = toggle(
    name = "verbose",
    default = Some(false),
    descrYes = "Print verbose output")
}

object OrientDatabaseBuilder {
  def main(args: Array[String]): Unit = {
    val conf = Conf(args)

    val verbose = conf.verbose.get.get
    val targetDir = new File(conf.targetDir.get.get)

    val files = for {
      rootDir <- verifyDirectoryExists(new File(conf.sourceDir.get.get), "rootDir")
      dataDir <- verifyDirectoryExists(new File(rootDir, conf.dataDir.get.get), "dataDir")
      deltaDir <- verifyDirectoryExists(new File(rootDir, conf.deltaDir.get.get), "deltaDir")
      databaseDir <- verifyDirectoryExists(new File(rootDir, conf.databaseDir.get.get), "databaseDir")
    } yield (rootDir, dataDir, deltaDir, databaseDir)

    files match {
      case Success((rootDir, dataDir, deltaDir, databaseDir)) =>
        val builder = new OrientDatabaseBuilder(
          rootDir,
          deltaDir,
          dataDir,
          databaseDir,
          targetDir,
          verbose)
        builder.buildAll()
      case Failure(f) =>
        println(f.getMessage)
    }
  }

  def verifyDirectoryExists(dir: File, optName: String): Try[File] = {
    if (!dir.exists()) {
      Failure(new IllegalArgumentException(s"Directory '${optName}' does not exist: ${dir}"))
    } else if (!dir.isDirectory()) {
      Failure(new IllegalArgumentException(s"'${optName}' is not a directory"))
    } else {
      Success(dir)
    }
  }
}

class OrientDatabaseBuilder(
    private[this] val rootDir: File,
    private[this] val deltaDir: File,
    private[this] val dataDir: File,
    private[this] val databaseDir: File,
    private[this] val targetDir: File,
    private[this] val verbose: Boolean) {

  private[this] val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())

  val mapper = new ObjectMapper(new YAMLFactory())
  implicit val f = DefaultFormats.withTypeHintFieldName("type") +
    ShortTypeHints(List(classOf[CreateClass], classOf[AlterClass], classOf[DropClass],
      classOf[AddProperty], classOf[AlterProperty], classOf[DropProperty],
      classOf[CreateIndex], classOf[DropIndex],
      classOf[CreateSequence], classOf[DropSequence],
      classOf[RunSQLCommand],
      classOf[CreateFunction], classOf[AlterFunction], classOf[DropFunction])) +
    new EnumNameSerializer(OrientType) +
    new EnumNameSerializer(IndexType) +
    new EnumNameSerializer(SequenceType)

  def buildAll(): Unit = {
    targetDir.mkdirs()

    val allFiles = recurseDirectory(databaseDir)
    allFiles.foreach { src =>
      val srcPath = src.getPath()
      val relPath = srcPath.substring(databaseDir.getPath.length(), srcPath.length())
      val target = new File(targetDir, relPath + ".gz")
      if (src.lastModified() > target.lastModified()) {
        println(s"'$srcPath' has been modified since last build.  Building.")
        buildDatabase(src, target)
      } else {
        println(s"'$srcPath' is already up to date.")
      }
    }
  }

  private[this] def buildDatabase(src: File, target: File): Unit = {
    if (verbose) {
      println("Creating temporary in memory database")
    }
    db.create()

    buildFromManifest(src, List()) flatMap { _ =>
      println(s"Building database build completed")
      exportDatabase(target)
    } recover {
      case t: Exception =>
        println(t.getMessage)
    }

    if (verbose) {
      println("Dropping temporary in memory database.")
    }

    db.drop()
    println("Done processing current database.")
  }

  private[this] def buildFromManifest(src: File, children: List[File]): Try[Unit] = {
    println(s"Building database from manifiest file: ${src.getPath}")
    val manifestData = fromFile(src).mkString
    val manifest = read[DatabaseManifest](manifestData)

    val newChildren = children :+ src

    for {
      schema <- applyDeltas(manifest.schemaVersion, deltaDir.getAbsolutePath, manifest.deltaDirectory)
      data <- processManifestScripts(manifest.dataScripts, dataDir, true)
    } yield ()
  }

  private[this] def applyDeltas(version: Int, deltaDir: String, deltaFolder: String): Try[Unit] = Try {
    val manager = new OrientSchemaManager(db, deltaDir, deltaFolder)
    manager.upgradeToVersion(version);
  }

  private[this] def processManifestScripts(scriptFiles: Option[List[String]], basePath: File, useTransaction: Boolean): Try[Unit] = Try {
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
        line => line.replace('\n', ' ').trim()
      }
      if (useTransaction) {
        db.begin()
      }

      val sql = mergedLines.mkString(";\n")
      println(sql)
      db.command(new OCommandScript(sql)).execute()

      if (useTransaction) {
        db.commit()
      }
    } finally {
      source.close()
    }
  }

  private[this] def exportDatabase(target: File): Try[Unit] = Try {
    println(s"Exporting database to ${target.getPath()}.")
    val export = new ODatabaseExport(db, target.getAbsolutePath, new OutputListener(verbose))
    export.exportDatabase()
    export.close()
    println("Export complete.")
    ()
  }

  private[this] def recurseDirectory(dir: File): Array[File] = {
    val these = dir.listFiles
    these ++ these.filter(f => f.isDirectory && f.getName().startsWith(".")).flatMap(recurseDirectory)
  }
}

private class OutputListener(verbose: Boolean) extends OCommandOutputListener() {
  def onMessage(text: String): Unit = {
    if (verbose) {
      println(text)
    }
  }
}
