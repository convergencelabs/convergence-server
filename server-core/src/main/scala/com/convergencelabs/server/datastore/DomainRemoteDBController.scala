package com.convergencelabs.server.datastore

import java.time.{ Duration => JavaDuration }
import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.JwtUtil
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.util.concurrent.FutureUtils.tryToFuture
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import grizzled.slf4j.Logging

object DomainRemoteDBController {
  val DefaultSnapshotConfig = ModelSnapshotConfig(
    false,
    false,
    false,
    250,
    1000,
    false,
    false,
    JavaDuration.of(0, ChronoUnit.MINUTES),
    JavaDuration.of(0, ChronoUnit.MINUTES))
}

case class DBConfig(dbName: String, username: String, password: String)

class DomainDBController(
  val orientDbConfig: Config,
  val domainDbConfig: Config,
  implicit val system: ActorSystem)
    extends Logging {

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

  val listener = new OCommandOutputListener() {
    def onMessage(message: String): Unit = {
      //logger.debug(message)
    }
  }

  def createDomain(
    dbName: String,
    dbPassword: String,
    importFile: Option[String]): Future[Unit] = {
    val uri = s"${BaseDbUri}/${dbName}"

    logger.debug(s"Creating domain database: $uri")
    val serverAdmin = new OServerAdmin(uri)
    serverAdmin.connect(AdminUser, AdminPassword)
      .createDatabase(DBType, StorageMode)
      .close()
    logger.debug(s"Domain database created at: $uri")

    // TODO we need to figure out how to set the admin user's password.
    val db = new ODatabaseDocumentTx(uri)
    db.open(AdminUser, AdminPassword)

    tryToFuture(Try {
      val f = importFile.getOrElse(Schema)
      logger.debug(s"Creating base domain schema from file '$f': $uri")
      val importer = new ODatabaseImport(db, f, listener)
      importer.importDatabase();
      importer.close();
      db.close()
      logger.debug(s"Base domain schema created: $uri")
    } flatMap { x =>
      initDomain(uri, dbPassword)
    })
  }

  private[this] def initDomain(uri: String, password: String): Try[Unit] = Try {
    logger.debug(s"Initializing domain: $uri")
    val pool = new OPartitionedDatabasePool(uri, Username, password, 64, 1)
    val persistenceProvider = new DomainPersistenceProvider(pool)
    persistenceProvider.validateConnection()
    logger.debug(s"Connected to domain database: $uri")
    persistenceProvider
  } flatMap {
    case persistenceProvider =>
      logger.debug(s"Generating admin key: $uri")
      JwtUtil.createKey().flatMap { rsaJsonWebKey =>
        for {
          publicKey <- JwtUtil.getPublicCertificatePEM(rsaJsonWebKey)
          privateKey <- JwtUtil.getPrivateKeyPEM(rsaJsonWebKey)
        } yield {
          new TokenKeyPair(publicKey, privateKey)
        }
      } flatMap { keyPair =>
        logger.debug(s"Created public key for domain: $uri")
        if (persistenceProvider.configStore.isInitialized().get) {
          logger.debug(s"Domain alreay initialized, updating keys: $uri")
          persistenceProvider.configStore.setAdminKeyPair(keyPair)
        } else {
          logger.debug(s"Domain not initialized, iniitalizing: $uri")
          persistenceProvider.configStore.initializeDomainConfig(
            keyPair,
            DomainRemoteDBController.DefaultSnapshotConfig)
        }
      } match {
        case s @ Success(_) =>
          logger.debug(s"Domain initialized: $uri")
          persistenceProvider.shutdown()
          s
        case f @ Failure(cause) =>
          logger.error(s"Failure initializing domain: $uri", cause)
          persistenceProvider.shutdown()
          f
      }
  }

  def deleteDomain(id: String): Unit = {
    val serverAdmin = new OServerAdmin(s"${BaseDbUri}/${id}")
    serverAdmin.connect(AdminUser, AdminPassword).dropDatabase(id).close()
  }
}
