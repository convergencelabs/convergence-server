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

private object UpgradeConf {
  def apply(arguments: Seq[String]): UpgradeConf = new UpgradeConf(arguments)
}

private class UpgradeConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Database Upgrader 0.1.0 (c) 2016 Convergence Labs")
  banner("Usage: -u url -d deltaVersion [-t type] [-l username] [-p password] [-dir deltasDirectory] [-v]")

  val dbUrl = opt[String](
    argName = "url",
    short = 'u',
    descr = "The location of the database",
    required = true)

  val deltaVersion = opt[Int](
    argName = "deltaVersion",
    short = 'd',
    descr = "The version to upgrade to",
    required = true)

  val dbType = opt[String](
    argName = "type",
    short = 't',
    descr = "The type of database",
    required = false,
    default = Some("Domain"))

  val dbUsername = opt[String](
    argName = "username",
    short = 'l',
    descr = "The database username",
    required = false,
    default = Some("admin"))

  val dbPassword = opt[String](
    argName = "password",
    short = 'p',
    descr = "The database password",
    required = false,
    default = Some("admin"))

  val deltaDir = opt[String](
    argName = "dir",
    descr = "The relative locatin of the delta dir relative to the root",
    required = false,
    default = Some("src/main/odb/deltas"))

  val verbose = toggle(
    name = "verbose",
    default = Some(false),
    descrYes = "Print verbose output")
}

object OrientDatabaseUpgrader {
  def main(args: Array[String]): Unit = {
    val conf = UpgradeConf(args)
    val dbUrl = conf.dbUrl.get.get
    val dbType = DBType.withName(conf.dbType.get.get)
    val username = conf.dbUsername.get.get
    val password = conf.dbPassword.get.get
    val deltaDir = conf.deltaDir.get.get
    val version = conf.deltaVersion.get.get
    val verbose = conf.verbose.get.get

    try {
    
    val db = new ODatabaseDocumentTx(dbUrl)
    db.open(username, password)

    val manager = new OrientSchemaManager(db, deltaDir, dbType)
    manager.upgradeToVersion(version)
    } finally {
      
    }
  }

  def verifyDirectoryExists(dir: String): Try[Unit] = {
    val file = new File(dir)
    if (!file.exists()) {
      Failure(new IllegalArgumentException(s"Directory '${dir}' does not exist: ${dir}"))
    } else if (!file.isDirectory()) {
      Failure(new IllegalArgumentException(s"'${dir}' is not a directory"))
    } else {
      Success(Unit)
    }
  }
}
