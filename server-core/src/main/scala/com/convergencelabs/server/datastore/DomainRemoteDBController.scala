package com.convergencelabs.server.datastore

import com.typesafe.config.Config
import com.convergencelabs.server.domain.Domain
import java.util.UUID
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.client.remote.OServerAdmin

class DomainRemoteDBController(domainConfig: Config) extends DomainDBController {

  val AdminUser = domainConfig.getString("admin-username")
  val AdminPassword = domainConfig.getString("admin-password")
  val AdminUri = domainConfig.getString("admin-uri")

  val Username = domainConfig.getString("username")
  val BaseUri = domainConfig.getString("uri")
  val Schema = domainConfig.getString("schema")
  
  val DBType = "document"
  val StorageMode = "plocal"
  

  def createDomain(): DBConfig = {
    val id = UUID.randomUUID().getLeastSignificantBits().toString()
    val password = UUID.randomUUID().getLeastSignificantBits.toString()

    val serverAdmin = new OServerAdmin(AdminUri)
    serverAdmin.connect(AdminUser, AdminPassword).createDatabase(id, DBType, StorageMode).close()

    val db = new ODatabaseDocumentTx(s"$BaseUri/$id")
    db.activateOnCurrentThread()
    db.open(Username, password)

    val dbImport = new ODatabaseImport(db, Schema, new OCommandOutputListener() { def onMessage(message: String) {} })
    dbImport.importDatabase()
    db.close()

    return DBConfig(id, Username, password)
  }

  def deleteDomain(id: String): Unit = {
    val serverAdmin = new OServerAdmin(AdminUri)
    serverAdmin.connect(AdminUser, AdminPassword).dropDatabase(id)
  }
}