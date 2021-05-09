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

package com.convergencelabs.convergence.server.backend.db

import java.time.Duration
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence._
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager.DomainDatabaseCreationData
import com.convergencelabs.convergence.server.backend.db.schema.SchemaManager.SchemaUpgradeError
import com.convergencelabs.convergence.server.backend.db.schema.{ConvergenceSchemaManager, SchemaManager}
import com.convergencelabs.convergence.server.backend.services.server.DomainDatabaseManagerActor.CreateDomainDatabaseResponse
import com.convergencelabs.convergence.server.backend.services.server.{DomainCreator, UserCreator}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.user.User
import com.convergencelabs.convergence.server.security.Roles
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.typesafe.config.Config
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
final class ConvergenceDatabaseInitializer(config: Config,
                                           ec: ExecutionContextExecutor) extends Logging {

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
      bootstrapped <- bootstrapIfDatabaseDoesNotExist(orientDb)
      _ <- if (!bootstrapped) upgradeConvergenceDatabaseIfNeeded() else Success(())
      _ <- if (!bootstrapped) autoConfigureServerAdminUserForExistingDatabase() else Success(())
      _ <- Try(orientDb.close())
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

  /**
   * Bootstraps the convergence database if it does not exist.
   *
   * @param orientDb The orientDb instance to use to create the dabatbase.
   * @return True if the convergence database did not exist and was
   *         initialized.
   */
  private[this] def bootstrapIfDatabaseDoesNotExist(orientDb: OrientDB): Try[Boolean] = {
    for {
      exists <- Try(orientDb.exists(convergenceDatabase))
      bootstrapped <- {
        if (exists) {
          Success(false)
        } else if (autoInstallEnabled) {
          bootstrapConvergenceDatabase(orientDb).map(_ => true)
        } else {
          Failure(new IllegalStateException("Convergence database does not exist and auto-install is not enabled"))
        }
      }
    } yield bootstrapped
  }

  /**
   * Creates the "convergence" database if it does not exist, and populates
   * it will default data.
   *
   * @return A Try that will be Success(()) if the convergence database is
   *         successfully initialized; Failure otherwise.
   */
  private[this] def bootstrapConvergenceDatabase(orientDb: OrientDB): Try[Unit] = {
    logger.info("Convergence database auto-install is enabled, checking if the Convergence database exists")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Convergence database does not exists, initializing it...")
      for {
        db <- createAndGetConvergenceDatabase(orientDb)
        dbProvider = new ConnectedSingleDatabaseProvider(db)
        _ <- configureConvergenceDatabaseUsers(db)
        _ <- installConvergenceSchema(dbProvider)
        // We need to also do this here because when we create the initial domains
        // we need to associate them with a user, so the admin user must be there
        // before hand.
        _ <- if (config.hasPath("convergence.default-server-admin")) {
          autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
        } else {
          Success(())
        }
        _ <- bootstrapData(dbProvider, config)
        _ <- Try(dbProvider.shutdown())
      } yield ()
    } else {
      logger.info("Convergence database already exists")
      Success(())
    }
  }

  /**
   * A helper method that will create the Convergence database according to the
   * Convergence Server config, and connect to it.
   *
   * @param orientDb The orientDb instance to use to administer databases.
   * @return The connected database or a Failure.
   */
  private[this] def createAndGetConvergenceDatabase(orientDb: OrientDB): Try[ODatabaseDocument] = Try {
    val adminUsername = dbServerConfig.getString("admin-username")
    val adminPassword = dbServerConfig.getString("admin-password")

    orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
    logger.debug("Convergence database created, connecting as database root user")

    val db = orientDb.open(convergenceDatabase, adminUsername, adminPassword)
    logger.info("Connected to convergence database")
    db
  }

  /**
   * A helper method that configures the database users that will be used to
   * connect to the Convergence Database.  OrientDB has default credentials
   * for a newly created database that are well known.  We do not want to use
   * these credentials. So we remove those uses and create new users with
   * proper credentials configured from the Convergence Server config.
   *
   * @param db A database instance connected to the Convergence database.
   */
  private[this] def configureConvergenceDatabaseUsers(db: ODatabaseDocument): Try[Unit] = Try {
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")

    logger.debug("Creating 'writer' user credentials.")
    db.getMetadata.getSecurity.createUser(username, password, "writer")

    logger.debug("Creating 'admin' user credentials.")
    db.getMetadata.getSecurity.createUser(adminUsername, adminPassword, "admin")

    ()
  }

  /**
   * A helper method that will install the Convergence database schema.
   *
   * @param dbProvider The database provider that is connected to
   *                   the database where the Convergence schema should
   *                   be installed to.
   */
  private def installConvergenceSchema(dbProvider: DatabaseProvider): Try[Unit] = {
    logger.info("Installing the Convergence database schema")
    val schemaManager = new ConvergenceSchemaManager(dbProvider)
    schemaManager
      .install()
      .fold(
        { _ =>
          Failure(new IllegalStateException("Convergence database schema install failed"))
        },
        { _ =>
          logger.info("Convergence database schema installation complete")
          Success(())
        }
      )
  }

  /**
   * A helper method that will reset the Convergence admin user information for
   * an existing convergence database.
   */
  private[this] def autoConfigureServerAdminUserForExistingDatabase(): Try[Unit] = {
    for {
      dbProvider <- createConvergenceDatabaseProvider()
      _ <- autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
    } yield ()
  }

  /**
   * Creates or updates the ConvergenceServer's default admin user.
   *
   * @param dbProvider [[DatabaseProvider]] that points to the convergence
   *                   database.
   * @param config     The ConvergenceServer's Config.
   */
  private[this] def autoConfigureServerAdmin(dbProvider: DatabaseProvider, config: Config): Try[Unit] = {
    logger.debug("Configuring default server admin user")
    val userStore = new UserStore(dbProvider)

    val username = config.getString("username")
    val password = config.getString("password")
    userStore
      .userExists(username)
      .flatMap { exists =>
        if (!exists) {
          createServerAdminUser(dbProvider, config, username, password)
        } else {
          logger.debug("Admin user exists, updating password.")
          userStore.setUserPassword(username, password)
        }
      }
      .recoverWith {
        case cause: Throwable =>
          logger.error("Error creating server admin user", cause)
          Failure(cause)
      }
  }

  /**
   * Creates the default server admin user. This must only be called when
   * initializing the Convergence schema when the admin user does not
   * exist.
   *
   * @param dbProvider A connection to the Convergence database.
   * @param config     The server's config.
   * @param username   The username to set.
   * @param password   The password for the suer.
   */
  private[this] def createServerAdminUser(dbProvider: DatabaseProvider,
                                          config: Config,
                                          username: String,
                                          password: String): Try[Unit] = {
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
    for {
      _ <- bootstrapDefaultConfigs(dbProvider, config)
      _ <- bootstrapDefaultNamespaces(dbProvider, config)
      _ <- bootstrapDomains(dbProvider, config)
    } yield ()
  }

  /**
   * Installs the default configurations from the server config file during
   * installation.
   *
   * @param dbProvider A provider connected to the convergence
   *                   database.
   * @param config     The servers config.
   */
  private def bootstrapDefaultConfigs(dbProvider: DatabaseProvider, config: Config): Try[Unit] = {
    val defaultConfigs = config.getConfig("convergence.bootstrap.default-configs")

    val configs = defaultConfigs
      .entrySet()
      .asScala
      .map(e => (e.getKey, e.getValue.unwrapped))
      .toMap

    val configStore = new ConfigStore(dbProvider)
    configStore.setConfigs(configs)
  }

  /**
   * Creates the default namespaces, per the sever config.
   *
   * @param dbProvider A provider connected to the convergence
   *                   database.
   * @param config     The servers config
   */
  private def bootstrapDefaultNamespaces(dbProvider: DatabaseProvider, config: Config): Try[Unit] = Try {
    val namespaceStore = new NamespaceStore(dbProvider)
    val namespaces = config.getConfigList("convergence.bootstrap.namespaces")
    namespaces.forEach { namespaceConfig =>
      val id = namespaceConfig.getString("id")
      val displayName = namespaceConfig.getString("displayName")
      logger.info(s"Bootstrapping namespace '$id'")
      namespaceStore.createNamespace(id, displayName, userNamespace = false).get
    }
  }

  /**
   * Creates the default domains, per the server config during installation.
   *
   * @param dbProvider A provider connected to the convergence
   *                   database.
   * @param config     The servers config
   */
  private def bootstrapDomains(dbProvider: DatabaseProvider, config: Config): Try[Unit] = Try {
    val namespaceStore = new NamespaceStore(dbProvider)
    val favoriteStore = new UserFavoriteDomainStore(dbProvider)
    val domainCreator = new InlineDomainCreator(dbProvider, config, ec)
    val domains = config.getConfigList("convergence.bootstrap.domains")
    val owner = config.getString("convergence.default-server-admin.username")

    domains.asScala.toList.foreach { domainConfig =>
      val namespace = domainConfig.getString("namespace")
      val id = domainConfig.getString("id")
      val displayName = domainConfig.getString("displayName")
      val favorite = domainConfig.getBoolean("favorite")
      val anonymousAuth = domainConfig.getBoolean("config.anonymousAuthEnabled")

      logger.info(s"Bootstrapping domain '$namespace/$id'")
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
            val f = domainCreator
              .createDomainDatabase(domainId, anonymousAuth, dbInfo)
              .map { _ =>
                logger.info(s"Bootstrapped domain '$namespace/$id'")

                if (favorite) {
                  val username = config.getString("convergence.default-server-admin.username")
                  favoriteStore.addFavorite(username, DomainId(namespace, id)).get
                }
              }
              .recover {
                case cause =>
                  logger.error(s"Error bootstrapping domain '$namespace/$id'", cause)
              }

            Await.ready(f, timeout.duration)
          }
      }).get
    }
  }

  def upgradeConvergenceDatabaseIfNeeded(): Try[Unit] = {
    createConvergenceAdminDatabaseProvider().flatMap { dbProvider =>
      checkVersionAndMaybeUpgrade(dbProvider).fold(
        {
          case SchemaManager.DeltaApplicationError(Some(cause)) =>
            Failure(cause)
          case SchemaManager.DeltaApplicationError(None) =>
            Failure(UpgradeException("An error occurred applying an upgrade delta"))
          case _: SchemaManager.DeltaValidationError =>
            Failure(UpgradeException("A delta failed to validate during upgrade."))
          case SchemaManager.RepositoryError(message) =>
            Failure(UpgradeException("A repository error occurred: " + message))
          case SchemaManager.StatePersistenceError(message) =>
            Failure(UpgradeException("A state persistence error occurred: " + message))
        },
        _ => Success(())
      )
    }
  }

  private def checkVersionAndMaybeUpgrade(dbProvider: DatabaseProvider): Either[SchemaUpgradeError, Unit] = {
    val schemaManager = new ConvergenceSchemaManager(dbProvider)
    val upgradeResult = for {
      latestVersion <- schemaManager.latestAvailableVersion()
      installedVersion <- schemaManager.currentlyInstalledVersion()
      _ <- if (latestVersion > installedVersion) {
        info(s"Convergence schema at version $installedVersion, but latest version is $latestVersion; performing upgrade")
        schemaManager.upgrade().map { _ =>
          info(s"Convergence schema upgrade complete")
        }
      } else {
        Right(())
      }
    } yield ()
    upgradeResult
  }

  private def createConvergenceDatabaseProvider(): Try[DatabaseProvider] = {
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    createDatabaseProvider(username, password)
  }

  private def createConvergenceAdminDatabaseProvider(): Try[DatabaseProvider] = {
    val username = convergenceDbConfig.getString("admin-username")
    val password = convergenceDbConfig.getString("admin-password")
    createDatabaseProvider(username, password)
  }

  private def createDatabaseProvider(username: String, password: String): Try[DatabaseProvider] = {
    val baseUri = dbServerConfig.getString("uri")
    val dbProvider = new SingleDatabaseProvider(baseUri, convergenceDatabase, username, password)
    dbProvider.connect().map(_ => dbProvider)
  }
}

private case class UpgradeException(message: String) extends RuntimeException(message)

/**
 * A helper class that will create a domain synchronously.
 */
private class InlineDomainCreator(provider: DatabaseProvider,
                                  config: Config,
                                  ec: ExecutionContext) extends DomainCreator(provider, config, ec) {
  private[this] val domainDatabaseManager = new DomainDatabaseManager(provider, config)

  override protected def createDomainDatabase(request: DomainDatabaseCreationData): Future[CreateDomainDatabaseResponse] = {
    FutureUtils.tryToFuture(domainDatabaseManager.createDomainDatabase(request).map(_ => CreateDomainDatabaseResponse(Right(Ok()))))
  }
}
