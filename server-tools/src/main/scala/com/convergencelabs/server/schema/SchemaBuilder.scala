package com.convergencelabs.server.util

import scala.io.Source._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.tool.ODatabaseExport
import com.orientechnologies.orient.core.command.script.OCommandScript
import org.rogach.scallop._
import scala.language.reflectiveCalls

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Database Builder 0.1.0 (c) 2015 Convergence Labs")
  banner("Usage: -i filenames -o filename [-v]")
  
  val inputFiles = opt[List[String]]("i", descr = "A comma separated list of osql source scripts", required = true)
  val outputFile = opt[String]("o", required = true)
  val verbose = opt[Boolean](argName = "verbose", required = false, default = Some(false))
      
  def apply(arguments: Seq[String]): Conf = new Conf(arguments)
}

object ConvergenceDatabaseBuilder {

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)

    val inputFiles = conf.inputFiles.get.get
    val outputFile = conf.outputFile.get.get
    val verbose = conf.verbose.get.get

    val builder = new ConvergenceDatabaseBuilder()
    builder.buildSchema(inputFiles, outputFile, verbose)
  }
}

class ConvergenceDatabaseBuilder {
  def buildSchema(inputFiles: List[String], outputFile: String, verbose: Boolean): Unit = {
    val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())
    db.create()

    inputFiles.foreach { filename =>
      val source = fromFile(filename)
      try {
        val script = source.getLines().filter { line =>
          (!line.trim().startsWith("#") && line.trim().size > 0)
        }.mkString("\n")

        db.command(new OCommandScript(script)).execute()
        db.commit()
      } finally {
        source.close()
      }
    }

    val export = new ODatabaseExport(db, outputFile, new OutputListener(verbose))
    export.exportDatabase()
    export.close()

    db.drop()
  }
}

private class OutputListener(verbose: Boolean) extends OCommandOutputListener() {
  def onMessage(text: String): Unit = {
    if (verbose) {
      println(text)
    }
  }
}