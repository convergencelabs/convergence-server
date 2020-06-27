/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.db

import java.time.Duration
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.ProvisionDomainResponse
import com.convergencelabs.convergence.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.Roles
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.typesafe.config.{Config, ConfigObject}
import grizzled.slf4j.Logging

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}


/**
 * A helper class that initializes Convergence on a fresh install and on
 * subsequent launches. Primarily it is concerned with installing the
 * convergence schema into OrientDB and ensuring that the default admin user
 * exists in the database.
 *
 * @param config The Config object for the Convergence Server
 * @param ec     An execution context to use for asynchronous operations.
 */
private[db] class ConvergenceDatabaseInitializer(private[this] val config: Config,
                                     private[this] val ec: ExecutionContextExecutor) extends Logging {

  private[this] val persistenceConfig = config.getConfig("convergence.persistence")
  private[this] val dbServerConfig = persistenceConfig.getConfig("server")
  private[this] val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")
  private[this] val convergenceDatabase = convergenceDbConfig.getString("database")
  private[this] val autoInstallEnabled = convergenceDbConfig.getBoolean("auto-install.enabled")

  /**
   * Asserts that the convergence database is initialized and ensures that that
   * default admin user exists and is properly configured.
   *
   * @return Success if the operation succeeds, or a Failure otherwise.
   */
  def assertInitialized(): Try[Unit] = {
    logger.debug("Processing request to ensure the convergence database is initialized")
    for {
      orientDb <- createOrientDb()
      exists <- convergenceDatabaseExists(orientDb)
      bootstrapped <- {
        if (exists) {
          Success(false)
        } else if (autoInstallEnabled) {
          this.bootstrapConvergenceDatabase(orientDb).map(_ => true)
        } else {
          Failure(new IllegalStateException("Convergence database does not exist and auto-install is not enabled"))
        }
      }
      _ <- {
        if (!bootstrapped) {
          val baseUri = dbServerConfig.getString("uri")
          val username = convergenceDbConfig.getString("username")
          val password = convergenceDbConfig.getString("password")

          val dbProvider = new SingleDatabaseProvider(baseUri, convergenceDatabase, username, password)
          dbProvider.connect().get
          autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
        } else {
          Success(())
        }
      }
    } yield {
      ()
    }
  }

  /**
   * A helper method to create and connect to the OrientDB server.
   *
   * @return A connected OrientDB or a Failure.
   */
  private[this] def createOrientDb(): Try[OrientDB] = Try {
    val retryDelay = convergenceDbConfig.getDuration("retry-delay")
    val uri = dbServerConfig.getString("uri")
    val serverAdminUsername = dbServerConfig.getString("admin-username")
    val serverAdminPassword = dbServerConfig.getString("admin-password")

    val connectTries = Iterator.continually(attemptConnection(uri, serverAdminUsername, serverAdminPassword, retryDelay))
    val orientDb = connectTries.dropWhile(_.isEmpty).next().get
    orientDb
  }

  private[this] def convergenceDatabaseExists(orientDb: OrientDB): Try[Boolean] = Try {
    orientDb.exists(convergenceDatabase)
  }

  /**
   * Creates the "convergence" database if it does not exist, and populates
   * it will default data.
   *
   * @return A Try that will be Success(()) if the convergence database is
   *         successfully initialized; Failure otherwise.
   */
  private[this] def bootstrapConvergenceDatabase(orientDb: OrientDB): Try[Unit] = Try {
    logger.info("auto-install is configured, attempting to connect to the database to determine if the convergence database is installed.")

    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")
    val preRelease = convergenceDbConfig.getBoolean("auto-install.pre-release")

    logger.info("Checking for Convergence database")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Convergence database does not exists.  Creating.")
      orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
      logger.debug("Convergence database created, connecting as default admin user and setting credentials")

      val db = orientDb.open(convergenceDatabase, "admin", "admin")
      logger.info("Connected to convergence database.")

      logger.debug("Deleting default 'reader' user.")
      db.getMetadata.getSecurity.getUser("reader").getDocument.delete()

      logger.debug("Setting 'writer' user credentials.")
      val writerUser = db.getMetadata.getSecurity.getUser("writer")
      writerUser.setName(username)
      writerUser.setPassword(password)
      writerUser.save()

      logger.debug("Setting 'admin' user credentials.")
      val adminUser = db.getMetadata.getSecurity.getUser("admin")
      adminUser.setName(adminUsername)
      adminUser.setPassword(adminPassword)
      adminUser.save()

      logger.info("Installing Convergence schema.")
      val dbProvider = new ConnectedSingleDatabaseProvider(db)
      val deltaHistoryStore = new DeltaHistoryStore(dbProvider)
      dbProvider.withDatabase { db =>
        val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
        schemaManager.install()
      }.map { _ =>
        logger.info("Schema installation complete")
      }.get

      // We need to also do this here because when we create the initial domains
      // we need to associate them with a user, so the admin user must be there
      // before hand.
      if (config.hasPath("convergence.default-server-admin")) {
        this.autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
      }

      bootstrapData(dbProvider, config)

      dbProvider.shutdown()
    } else {
      logger.info("Convergence database already exists.")
    }

    orientDb.close()
    ()
  }

  /**
   * Creates or updates the ConvergenceServer's default admin user.
   *
   * @param dbProvider [[com.convergencelabs.convergence.server.db.DatabaseProvider]] that points to the convergence database.
   * @param config     The ConvergenceServer's Config.
   */
  private[this] def autoConfigureServerAdmin(dbProvider: DatabaseProvider, config: Config): Try[Unit] = {
    logger.debug("Configuring default server admin user")
    val userStore = new UserStore(dbProvider)

    val username = config.getString("username")
    val password = config.getString("password")
    userStore.userExists(username).flatMap { exists =>
      if (!exists) {
        logger.debug("Admin user does not exist, creating.")
        val userCreator = new UserCreator(dbProvider)

        val firstName = config.getString("firstName")
        val lastName = config.getString("lastName")
        val displayName = if (config.hasPath("displayName")) {
          config.getString("displayName")
        } else {
          "Server Admin"
        }
        val email = config.getString("email")

        val user = User(username, email, firstName, lastName, displayName, None)
        userCreator.createUser(user, password, Roles.Server.ServerAdmin)
      } else {
        logger.debug("Admin user exists, updating password.")
        userStore.setUserPassword(username, password)
      }
    }.recover {
      case cause: Throwable =>
        logger.error("Error creating server admin user", cause)
    }
  }

  /**
   * Installs default data into the Convergence database.
   *
   * @param dbProvider A [[DatabaseProvider]] which is connected to the
   *                   Convergence Database.
   * @param config     The server's config.
   * @return An indication of success or failure.
   */
  private[this] def bootstrapData(dbProvider: DatabaseProvider, config: Config): Try[Unit] = {
    val bootstrapConfig = config.getConfig("convergence.bootstrap")
    val defaultConfigs = bootstrapConfig.getConfig("default-configs")

    val owner = config.getString("convergence.default-server-admin.username")

    val configs =
      defaultConfigs.entrySet().asScala
      .map(e => (e.getKey, e.getValue.unwrapped))
      .toMap

    val configStore = new ConfigStore(dbProvider)
    val favoriteStore = new UserFavoriteDomainStore(dbProvider)
    val domainCreator = new InlineDomainCreator(dbProvider, config, ec)
    val namespaceStore = new NamespaceStore(dbProvider)
    configStore.setConfigs(configs)

    val namespaces = bootstrapConfig.getList("namespaces")
    namespaces.forEach {
      case obj: ConfigObject =>
        val c = obj.toConfig
        val id = c.getString("id")
        val displayName = c.getString("displayName")
        logger.info(s"bootstrapping namespace '$id'")
        namespaceStore.createNamespace(id, displayName, userNamespace = false).get
    }

    val domains = bootstrapConfig.getList("domains")
    domains.asScala.toList.foreach {
      case obj: ConfigObject =>
        val c = obj.toConfig
        val namespace = c.getString("namespace")
        val id = c.getString("id")
        val displayName = c.getString("displayName")
        val favorite = c.getBoolean("favorite")
        val anonymousAuth = c.getBoolean("config.anonymousAuthEnabled")

        logger.info(s"bootstrapping domain '$namespace/$id'")
        (for {
          exists <- namespaceStore.namespaceExists(namespace)
          _ <- if (!exists) {
            Failure(new IllegalArgumentException("The namespace for a bootstrapped domain, must also be bootstrapped"))
          } else {
            Success(())
          }
        } yield {
          implicit val ec: ExecutionContextExecutor = ExecutionContext.global
          val domainId = DomainId(namespace, id)
          val timeout = Timeout(4, TimeUnit.MINUTES)
          domainCreator.createDomain(domainId, displayName, owner)
            .map { dbInfo =>
              val f = domainCreator.provisionDomain(domainId, anonymousAuth, dbInfo)
              Await.ready(f, timeout.duration)
              logger.info(s"bootstrapped domain '$namespace/$id'")

              if (favorite) {
                val username = config.getString("convergence.default-server-admin.username")
                favoriteStore.addFavorite(username, DomainId(namespace, id)).get
              }
          }
        }).get
    }
    Success(())
  }

  /**
   * Attempts to connect to OrientDB.
   *
   * @param uri           The URI of the OrientDB server.
   * @param adminUser     The username of a privileged user in OrientDB that will be
   *                      used to create databases.
   * @param adminPassword The password of the privileged OrientDB user.
   * @param retryDelay    How long to wait between connection attempts.
   * @return Some if / when OrientDB is successfully connected to; None if the attempt fails.
   */
  private[this] def attemptConnection(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration): Option[OrientDB] = {
    info(s"Attempting to connect to the database at uri: $uri")

    Try(new OrientDB(uri, adminUser, adminPassword, OrientDBConfig.defaultConfig())) match {
      case Success(db) =>
        logger.info("Connected to database with Server Admin")
        Some(db)
      case Failure(e) =>
        logger.error(s"Unable to connect to database, retrying in ${retryDelay.toMillis}ms", e)
        Thread.sleep(retryDelay.toMillis)
        None
    }
  }
}

/**
 * A helper class that will create a domain synchronously.
 */
private class InlineDomainCreator(provider: DatabaseProvider,
                                  config: Config,
                                  ec: ExecutionContext) extends DomainCreator(provider, config, ec) {
  private val provisioner = new DomainProvisioner(provider, config)

  override def provisionDomain(request: ProvisionRequest): Future[ProvisionDomainResponse] = {
    FutureUtils.tryToFuture(provisioner.provisionDomain(request).map(_ => ProvisionDomainResponse(Right(Ok()))))
  }
}
