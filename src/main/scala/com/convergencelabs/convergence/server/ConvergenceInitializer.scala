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

package com.convergencelabs.convergence.server

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.convergence.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.convergence.server.db.{ConnectedSingleDatabaseProvider, DatabaseProvider}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.Roles
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.typesafe.config.{Config, ConfigObject}
import grizzled.slf4j.Logging

import scala.collection.JavaConverters
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
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
class ConvergenceInitializer(private[this] val config: Config,
                             private[this] val ec: ExecutionContextExecutor) extends Logging {

  /**
   * Creates the "convergence" database if it does not exist, and populates
   * it will default data.
   *
   * @param config The ConvergenceServer's Config.
   * @return A Try that will be Success(()) if the convergence database is
   *         successfully initialized; Failure otherwise.
   */
  def bootstrapConvergenceDatabase(config: Config): Try[Unit] = Try {
    val persistenceConfig = config.getConfig("convergence.persistence")
    val dbServerConfig = persistenceConfig.getConfig("server")
    val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

    logger.info("auto-install is configured, attempting to connect to the database to determine if the convergence database is installed.")

    val convergenceDatabase = convergenceDbConfig.getString("database")
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")
    val preRelease = convergenceDbConfig.getBoolean("auto-install.pre-release")
    val retryDelay = convergenceDbConfig.getDuration("retry-delay")

    val uri = dbServerConfig.getString("uri")
    val serverAdminUsername = dbServerConfig.getString("admin-username")
    val serverAdminPassword = dbServerConfig.getString("admin-password")

    val connectTries = Iterator.continually(attemptConnect(uri, serverAdminUsername, serverAdminPassword, retryDelay))
    val orientDb = connectTries.dropWhile(_.isEmpty).next().get

    logger.info("Checking for Convergence database")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Convergence database does not exists.  Creating.")
      orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
      logger.debug("Convergence database created, connecting as default admin user")

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
  def autoConfigureServerAdmin(dbProvider: DatabaseProvider, config: Config): Unit = {
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

    val configs = JavaConverters
      .asScalaSet(defaultConfigs.entrySet())
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
    JavaConverters.asScalaBuffer(domains).toList.foreach {
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
          val f = domainCreator.createDomain(namespace, id, displayName, anonymousAuth).get.map { _ =>
            logger.info(s"bootstrapped domain '$namespace/$id'")
          }(ec)

          Await.ready(f, FiniteDuration.apply(2, TimeUnit.MINUTES))

          if (favorite) {
            val username = config.getString("convergence.default-server-admin.username")
            favoriteStore.addFavorite(username, DomainId(namespace, id)).get
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
  private[this] def attemptConnect(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration): Option[OrientDB] = {
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

  def provisionDomain(request: ProvisionDomain): Future[Unit] = {
    val ProvisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth) = request
    FutureUtils.tryToFuture(provisioner.provisionDomain(domainId, databaseName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth))
  }
}
