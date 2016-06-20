package com.convergencelabs.server.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.client.remote.OServerAdmin
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.io.InputStream

object ImportTester {

  def main(args: Array[String]): Unit = {
    val remote = true

    val uri = remote match {
      case false => s"memory:${System.currentTimeMillis()}${System.nanoTime()}";
      case true => s"remote:demo-db.convergencelabs.tech/test_import-${System.nanoTime()}"
    }

    if (remote) {
      val serverAdmin = new OServerAdmin(uri)
      serverAdmin.connect("root", "password").createDatabase("document", "plocal").close()
    }

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()

    if (!remote) {
      db.create()
    }

    db.open("admin", "admin")

    val dbImport = new ODatabaseImport(db, "target/odb/domain-n1-d1.json.gz", new OCommandOutputListener() {
      def onMessage(message: String): Unit = {
        println(message)
      }
    })

    dbImport.importDatabase()
    db.close()
  }
}