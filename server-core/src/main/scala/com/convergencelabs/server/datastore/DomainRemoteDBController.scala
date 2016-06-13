package com.convergencelabs.server.datastore

import com.typesafe.config.Config
import com.convergencelabs.server.domain.Domain
import java.util.UUID
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.client.remote.OServerAdmin
import scala.concurrent.Future
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.Http
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpHeader
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext
import grizzled.slf4j.Logging
import scala.io.Source

class DomainRemoteDBController(
  domainConfig: Config,
  implicit val system: ActorSystem)
    extends DomainDBController
    with Logging {

  val AdminUser = domainConfig.getString("admin-username")
  val AdminPassword = domainConfig.getString("admin-password")

  val Username = domainConfig.getString("username")
  val DefaultPassword = domainConfig.getString("default-password")
  val BaseUri = domainConfig.getString("uri")
  val Schema = domainConfig.getString("schema")

  val DBType = "document"
  val StorageMode = "plocal"

  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def createDomain(importFile: Option[String]): DBConfig = {
    val id = UUID.randomUUID().getLeastSignificantBits().toString()

    val uri = s"${BaseUri}/${id}"
    logger.debug(s"Creating domain database: $uri")

    val serverAdmin = new OServerAdmin(uri)
    serverAdmin.connect(AdminUser, AdminPassword).createDatabase(DBType, StorageMode).close()
    logger.debug(s"domain database created at: $uri")

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.open(Username, DefaultPassword)

    // FIXME need a separate param for this uri
    val orientRestUrl = "http://192.168.99.100:2480"

    val connectApi = s"${orientRestUrl}/connect/convergence"
    logger.debug(s"Connecting to orientdb: $connectApi")

    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = connectApi)) onComplete {
        case Success(r) => debug(r)
        case Failure(f) => f.printStackTrace()
      }

    val importApi = s"${orientRestUrl}/import/${id}"
    logger.debug(s"Making rest call to import domain schema: $importApi")

    DBConfig(id, Username, DefaultPassword)
  }

  def deleteDomain(id: String): Unit = {
    val serverAdmin = new OServerAdmin(s"${BaseUri}/${id}")
    serverAdmin.connect(AdminUser, AdminPassword).dropDatabase(id).close()
  }
}
