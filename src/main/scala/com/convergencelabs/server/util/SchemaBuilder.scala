package com.convergencelabs.server.util

import scala.io.Source._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.tool.ODatabaseExport
import com.orientechnologies.orient.core.command.script.OCommandScript
import org.rogach.scallop._
import scala.language.reflectiveCalls

object SchemaBuilder {

  def main(args: Array[String]): Unit = {
    val opts = new ScallopConf(args) {
      banner("Usage: -i filenames -o filename")
      val inputFiles = opt[List[String]]("i", required = true)
      val outputFile = opt[String]("o", required = true)
    }

    val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())
    db.create()

    opts.inputFiles.get.get.foreach { filename =>
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

    val listener = new OCommandOutputListener() {
      def onMessage(text: String): Unit = {}
    }

    val export = new ODatabaseExport(db, opts.outputFile.get.getOrElse("database.export.osql"), listener)
    export.exportDatabase()
    export.close()

    db.close()
    db.drop()
  }
}