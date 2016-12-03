package com.convergencelabs.server.db.schema

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.io.Source.fromFile
import scala.language.reflectiveCalls
import scala.util.Try

import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.Serialization.read
import org.rogach.scallop.ScallopConf

import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseExport

private object Conf {
  def apply(arguments: Seq[String]): Conf = new Conf(arguments)
}

private class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Database Builder 0.1.0 (c) 2015 Convergence Labs")
  banner("Usage: --type [domain | convergence] --target filename --verbose")

  val `type` = opt[String](
    argName = "type",
    short = 'd',
    descr = "The type of database",
    required = true)

  val output = opt[String](
    argName = "output",
    short = 'o',
    descr = "The location of the target folder",
    required = true)

  val verbose = toggle(
    name = "verbose",
    default = Some(false),
    descrYes = "Print verbose output")

  val preRelease = toggle(
    name = "pre-release",
    default = Some(false),
    descrYes = "Print verbose output")
}

object DatabaseExportBuilder {
  def main(args: Array[String]): Unit = {
    val conf = Conf(args)
    conf.verify()
    val verbose = conf.verbose.toOption.get
    val targetDir = new File(conf.output.toOption.get)

    val deltaCat = conf.`type`.toOption.get match {
      case "domain" =>
        DeltaCategory.Domain
      case "convergence" =>
        DeltaCategory.Convergence
    }

    val prePrelease = conf.preRelease.toOption.get

    val builder = new DatabaseExportBuilder(deltaCat, prePrelease, targetDir, verbose)
    builder.build()
  }
}

class DatabaseExportBuilder(
    private[this] val category: DeltaCategory.Value,
    private[this] val preRelease: Boolean,
    private[this] val outputFile: File,
    private[this] val verbose: Boolean) {

  if (ODatabaseRecordThreadLocal.INSTANCE == null) {
    sys.error("Calling this manually apparently prevents an initialization issue.")
  }

  def build(): Unit = {
    
//    val dbName = "memory:export" + System.nanoTime()
//    val createDb = new ODatabaseDocumentTx(dbName)
//    createDb.create()
//    
//    val dbPool = new OPartitionedDatabasePool(dbName, "admin", "admin")
//    val schemaManager = new DatabaseSchemaManager(dbPool, category, preRelease)
//
//    schemaManager.upgradeToLatest()
//    val db = dbPool.acquire()
//    
//    outputFile.getParentFile.mkdirs()
//    
//    val export = new ODatabaseExport(db, outputFile.getAbsolutePath, new OutputListener(verbose))
//    export.exportDatabase()
//    export.close()
//    
//    dbPool.close()
//    
//    createDb.activateOnCurrentThread()
//    createDb.close()
//    
//    println("Export complete.")
    ()
  }
}

private class OutputListener(verbose: Boolean) extends OCommandOutputListener() {
  def onMessage(text: String): Unit = {
    if (verbose) {
      println(text)
    }
  }
}
