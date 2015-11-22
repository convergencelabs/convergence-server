package com.convergencelabs.server.util

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseExport
import com.orientechnologies.orient.core.command.OCommandOutputListener

object ExportTest extends App {
  val database = new ODatabaseDocumentTx("memory:oo")
  database.create()
  //database.open("admin", "admin")
  val xport = new ODatabaseExport(database, "./file.json", new OCommandOutputListener() {
    def onMessage(text: String): Unit ={
      println(text)
    }
  })
  
  xport.setOptions("-noCompression -excludeClass=\"OFunction OIdentity\"")
  
  xport.exportDatabase()
}