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
import com.convergencelabs.server.schema.OrientSchemaManager
import com.convergencelabs.server.schema.DBType.Domain
import com.orientechnologies.orient.core.metadata.security.OUser

import DomainRemoteDBController._
import com.convergencelabs.server.domain.DomainDatabaseInfo

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
    
  val OrientDefaultAdmin = "admin"
  val OrientDefaultReader = "reader"
  val OrientDefaultWriter = "writer"
}

case class DBConfig(dbName: String, username: String, password: String)

class DomainDBController(
  val orientDbConfig: Config,
  implicit val system: ActorSystem)
    extends Logging {

  val AdminUser = orientDbConfig.getString("admin-username")
  val AdminPassword = orientDbConfig.getString("admin-password")
  val BaseDbUri = orientDbConfig.getString("db-uri")

  val DBType = "document"
  val StorageMode = "plocal"

  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val listener = new OCommandOutputListener() {
    def onMessage(message: String): Unit = {
      //logger.debug(message)
    }
  }

  def createDomain(dbInfo: DomainDatabaseInfo): Future[Unit] = {
    val DomainDatabaseInfo(dbName, adminUsername, adminPassword, normalUsername, normalPassword) = dbInfo
    val uri = s"${BaseDbUri}/${dbName}"

    logger.debug(s"Creating domain database: $uri")
    val serverAdmin = new OServerAdmin(uri)
    serverAdmin.connect(AdminUser, AdminPassword)
      .createDatabase(DBType, StorageMode)      
      .close()
    logger.debug(s"Domain database created at: $uri")

    // Orient DB has three default users. admin, reader and writer. They all 
    // get created with their passwords equal to their usernames. We want
    // to change the admin and writer and delete the reader.
    val db = new ODatabaseDocumentTx(uri)
    db.open(AdminUser, AdminPassword)
    
    // Change the admin username / password and then reconnect
    val adminUser = db.getMetadata().getSecurity().getUser(OrientDefaultAdmin)
    adminUser.setName(adminUsername)
    adminUser.setPassword(adminPassword)
    adminUser.save()
    
    // Close and reconnect with the new credentials to make sure everything
    // we set properly.
    db.close()
    db.open(adminUsername, adminPassword)
    
    // Change the username and password of the normal user
    val normalUser = db.getMetadata().getSecurity().getUser(OrientDefaultWriter)
    normalUser.setName(normalUsername)
    normalUser.setPassword(normalPassword)
    normalUser.save()
    
    // Delete the reader user since we do not need it.
    db.getMetadata().getSecurity().getUser(OrientDefaultReader).getDocument().delete()
    
    tryToFuture(Try {
      // FIXME make sure this becomes asynchronous.
      val schemaManager = new OrientSchemaManager(db, Domain)
      
      // FIXME make this a config
      schemaManager.upgradeToVersion(1)
      db.close()
      logger.debug(s"Base domain schema created: $uri")
    } flatMap { x =>
      initDomain(uri, adminUsername, adminPassword)
    })
  }

  private[this] def initDomain(uri: String, username: String, password: String): Try[Unit] = Try {
    logger.debug(s"Initializing domain: $uri")
    val pool = new OPartitionedDatabasePool(uri, username, password, 64, 1)
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

  def deleteDomain(dbName: String): Try[Unit] = Try {
    val serverAdmin = new OServerAdmin(s"${BaseDbUri}/${dbName}")
    serverAdmin
      .connect(AdminUser, AdminPassword)
      .dropDatabase(StorageMode)
      .close()
  }
}
