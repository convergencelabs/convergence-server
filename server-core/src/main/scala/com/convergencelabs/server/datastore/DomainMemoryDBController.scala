package com.convergencelabs.server.datastore

import com.typesafe.config.Config
import com.convergencelabs.server.domain.Domain
import java.util.UUID
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener

class DomainMemoryDBController(domainConfig: Config) extends DomainDBController {

  val Username = domainConfig.getString("username")
  val BaseUri = domainConfig.getString("uri")
  val Schema = domainConfig.getString("schema")
  val Password = "admin"

  def createDomain(): DBConfig = {
    val id = UUID.randomUUID().getLeastSignificantBits().toString()

    val db = new ODatabaseDocumentTx(s"$BaseUri/$id")
    db.activateOnCurrentThread()
    db.create()

    val dbImport = new ODatabaseImport(db, Schema, new OCommandOutputListener() {
      def onMessage(message: String) {}
    })

    dbImport.importDatabase()
    db.close()

    DBConfig(id, Username, Password)
  }

  def deleteDomain(id: String): Unit = {
    val db = new ODatabaseDocumentTx(s"$BaseUri/$id")
    db.activateOnCurrentThread()

    if (db.isClosed()) {
      db.open(Username, Password)
    }

    db.drop()
    db.activateOnCurrentThread()
    db.close()
  }
}
