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
import akka.http.scaladsl.model.headers._
import scala.concurrent.duration.Duration
import scala.concurrent.Await

class DomainRemoteDBController(
  val orientDbConfig: Config,
  val domainDbConfig: Config,
  implicit val system: ActorSystem)
    extends DomainDBController
    with Logging {

  val AdminUser = orientDbConfig.getString("admin-username")
  val AdminPassword = orientDbConfig.getString("admin-password")
  val BaseDbUri = orientDbConfig.getString("db-uri")
  val BaseRestUri = orientDbConfig.getString("rest-uri")

  val Username = domainDbConfig.getString("username")
  val DefaultPassword = domainDbConfig.getString("default-password")
  val Schema = domainDbConfig.getString("schema")

  val DBType = "document"
  val StorageMode = "plocal"

  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def createDomain(importFile: Option[String]): DBConfig = {
    val id = UUID.randomUUID().getLeastSignificantBits().toString()

    val uri = s"${BaseDbUri}/${id}"
    logger.debug(s"Creating domain database: $uri")

    val serverAdmin = new OServerAdmin(uri)
    serverAdmin.connect(AdminUser, AdminPassword).createDatabase(DBType, StorageMode).close()
    logger.debug(s"domain database created at: $uri")

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.open(Username, DefaultPassword)

    val importContents = Source.fromFile(importFile.getOrElse(Schema)).mkString
    val importApi = s"${BaseRestUri}/import/${id}"

    // FIXME A bit of a hack, but don't feel like messing with futures at the moment.
    val importEntity = Await.result(Marshal(importContents).to[RequestEntity], Duration.Inf)
    val authHeader = Authorization(BasicHttpCredentials("admin", "admin"))
    val importPost = HttpRequest(method = HttpMethods.POST,
      uri = importApi, headers = List(authHeader), entity = importEntity)

    Http().singleRequest(importPost) onComplete {
      case Success(r) =>
        logger.debug(s"Import Success: $r")
      case Failure(f) =>
        f.printStackTrace()
    }

    DBConfig(id, Username, DefaultPassword)
  }

  def deleteDomain(id: String): Unit = {
    val serverAdmin = new OServerAdmin(s"${BaseDbUri}/${id}")
    serverAdmin.connect(AdminUser, AdminPassword).dropDatabase(id).close()
  }
}
