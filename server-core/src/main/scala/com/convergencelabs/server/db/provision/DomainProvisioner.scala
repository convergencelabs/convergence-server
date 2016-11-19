package com.convergencelabs.server.db.provision

import java.time.{ Duration => JavaDuration }
import java.time.temporal.ChronoUnit

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.JwtUtil
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.schema.DatabaseSchemaManager
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

import DomainProvisioner.DBType
import DomainProvisioner.DefaultSnapshotConfig
import DomainProvisioner.OrientDefaultAdmin
import DomainProvisioner.OrientDefaultReader
import DomainProvisioner.OrientDefaultWriter
import DomainProvisioner.StorageMode
import grizzled.slf4j.Logging
import com.convergencelabs.db.deltas.DeltaCategory

object DomainProvisioner {
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

  val DBType = "document"
  val StorageMode = "plocal"
}

class DomainProvisioner(
  dbBaseUri: String,
  dbRootUsername: String,
  dbRootPasword: String)
    extends Logging {

  def provisionDomain(dbName: String, dbUsername: String, dbPassword: String, dbAdminUsername: String, dbAdminPassword: String): Try[Unit] = {
    val dbUri = computeDbUri(dbName)
    logger.debug(s"Provisioning domain: $dbUri")
    createDatabase(dbUri) flatMap { _ =>
      setAdminCredentials(dbUri, dbAdminUsername, dbAdminPassword)
    } flatMap { _ =>
      val dbPool = new OPartitionedDatabasePool(dbUri, dbAdminUsername, dbAdminPassword)
      val t = configureNonAdminUsers(dbPool, dbUsername, dbPassword) flatMap { _ =>
        installSchema(dbPool)
      }
      logger.debug(s"Disconnecting as admin user: $dbUri")
      dbPool.close()
      t
    } flatMap { _ =>
      initDomain(dbUri, dbUsername, dbPassword)
    }
  }

  private[this] def createDatabase(dbUri: String): Try[Unit] = Try {
    logger.debug(s"Creating domain database: $dbUri")
    val serverAdmin = new OServerAdmin(dbUri)
    serverAdmin.connect(dbRootUsername, dbRootPasword)
      .createDatabase(DBType, StorageMode)
      .close()
    logger.debug(s"Domain database created at: $dbUri")
  }

  private[this] def setAdminCredentials(dbUri: String, adminUsername: String, adminPassword: String): Try[Unit] = Try {
    logger.debug(s"Updating database admin credentials: $dbUri")
    // Orient DB has three default users. admin, reader and writer. They all 
    // get created with their passwords equal to their usernames. We want
    // to change the admin and writer and delete the reader.
    val db = new ODatabaseDocumentTx(dbUri)
    db.open(OrientDefaultAdmin, OrientDefaultAdmin)

    // Change the admin username / password and then reconnect
    val adminUser = db.getMetadata().getSecurity().getUser(OrientDefaultAdmin)
    adminUser.setName(adminUsername)
    adminUser.setPassword(adminPassword)
    adminUser.save()

    logger.debug(s"Database admin credentials set, reconnecting: $dbUri")

    // Close and reconnect with the new credentials to make sure everything
    // we set properly.
    db.close()
  }

  private[this] def configureNonAdminUsers(dbPool: OPartitionedDatabasePool, dbUsername: String, dbPassword: String): Try[Unit] = Try {
    val db = dbPool.acquire()
    logger.debug(s"Updating normal user credentials: ${db.getURL}")

    // Change the username and password of the normal user
    val normalUser = db.getMetadata().getSecurity().getUser(OrientDefaultWriter)
    normalUser.setName(dbUsername)
    normalUser.setPassword(dbPassword)
    normalUser.save()

    logger.debug(s"Deleting 'reader' user credentials: ${db.getURL}")
    // Delete the reader user since we do not need it.
    db.getMetadata().getSecurity().getUser(OrientDefaultReader).getDocument().delete()
    db.close()
    ()
  }

  private[this] def installSchema(dbPool: OPartitionedDatabasePool): Try[Unit] = {
    val schemaManager = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain)
    logger.debug(s"Installing domain db schema to: ${dbPool.getUrl}")
    schemaManager.upgradeToLatest() map { _ =>
      logger.debug(s"Base domain schema created: ${dbPool.getUrl}")
    }
  }

  private[this] def initDomain(uri: String, username: String, password: String): Try[Unit] = {
    logger.debug(s"Connecting as normal user to initialize domain: ${uri}")
    val pool = new OPartitionedDatabasePool(uri, username, password)
    val persistenceProvider = new DomainPersistenceProvider(pool)
    persistenceProvider.validateConnection() map (_ => persistenceProvider)
  } flatMap {
    persistenceProvider =>
      logger.debug(s"Connected to domain database: ${uri}")

      logger.debug(s"Generating admin key: ${uri}")
      JwtUtil.createKey().flatMap { rsaJsonWebKey =>
        for {
          publicKey <- JwtUtil.getPublicCertificatePEM(rsaJsonWebKey)
          privateKey <- JwtUtil.getPrivateKeyPEM(rsaJsonWebKey)
        } yield {
          new JwtKeyPair(publicKey, privateKey)
        }
      } flatMap { keyPair =>
        logger.debug(s"Created public key for domain: ${uri}")

        logger.debug(s"Iniitalizing domain: ${uri}")
        persistenceProvider.configStore.initializeDomainConfig(
          keyPair,
          DefaultSnapshotConfig)
      } map { _ =>
        logger.debug(s"Domain initialized: ${uri}")
        persistenceProvider.shutdown()
      } recoverWith {
        case cause: Exception =>
          logger.error(s"Failure initializing domain: ${uri}", cause)
          persistenceProvider.shutdown()
          Failure(cause)
      }
  }

  def destroyDomain(dbName: String): Try[Unit] = Try {
    val dbUri = computeDbUri(dbName)
    logger.debug(s"Deleting database at: ${dbUri}")
    val serverAdmin = new OServerAdmin(dbUri)
    serverAdmin
      .connect(dbRootUsername, dbRootPasword)
      .dropDatabase(StorageMode)
      .close()
  }

  private[this] def computeDbUri(dbName: String): String = {
    s"${dbBaseUri}/${dbName}"
  }
}
