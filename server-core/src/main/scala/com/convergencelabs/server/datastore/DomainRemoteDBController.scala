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

  val Username = domainConfig.getString("username")
  val BaseUri = domainConfig.getString("uri")
  val Schema = domainConfig.getString("schema")

  val DBType = "document"
  val StorageMode = "plocal"

  def createDomain(importFile: Option[String]): DBConfig = {
    val id = UUID.randomUUID().getLeastSignificantBits().toString()
    val password = UUID.randomUUID().getLeastSignificantBits.toString()
    val uri = s"${BaseUri}/${id}"

    val serverAdmin = new OServerAdmin(uri)
    serverAdmin.connect(AdminUser, AdminPassword).createDatabase(DBType, StorageMode).close()

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.open(Username, password)

    val dbImport = new ODatabaseImport(db, importFile.getOrElse(Schema), new OCommandOutputListener() {
      def onMessage(message: String): Unit = {}
    })

    dbImport.importDatabase()
    db.close()

    DBConfig(id, Username, password)
  }

  def deleteDomain(id: String): Unit = {
    val serverAdmin = new OServerAdmin(s"BaseUri/$id")
    serverAdmin.connect(AdminUser, AdminPassword).dropDatabase(id).close()
  }
}
