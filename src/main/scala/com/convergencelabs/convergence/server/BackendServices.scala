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

import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Scheduler, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, ClusterSingletonSettings, SingletonActor}
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.convergence.server.db.provision.{DomainLifecycleTopic, DomainProvisioner, DomainProvisionerActor}
import com.convergencelabs.convergence.server.db.schema.{DatabaseManager, DatabaseManagerActor}
import com.convergencelabs.convergence.server.db.{DatabaseProvider, PooledDatabaseProvider}
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.{Success, Try}

/**
 * The [[BackendServices]] class is the main entry point that bootstraps the
 * core business logic services in the Convergence Server. It is responsible
 * for start that various Akka Actors the comprise the major subsystems (
 * Chat, Presence, Models, etc.).
 *
 * @param context              The Akka ActorContext to start Actors in.
 * @param domainLifecycleTopic The topic to use for domain lifecycle events.
 */
class BackendServices(context: ActorContext[_],
                      domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]
                     ) extends Logging {

  private[this] var convergenceDbProvider: Option[DatabaseProvider] = None

  /**
   * Starts the Backend Services. Largely this method will start up all
   * of the Actors required to provide e the core Convergence Server
   * services.
   */
  def start(): Try[Unit] = {
    logger.info("Convergence Backend Services starting up...")
    val config = this.context.system.settings.config
    val persistenceConfig = config.getConfig("convergence.persistence")
    for {
      provider <- createPool(persistenceConfig)
      _ <- createDomainPersistenceManager(persistenceConfig, provider)
      _ <- startActors(persistenceConfig, provider)
    } yield {
      logger.info("Convergence Backend Services started up.")
    }
  }

  /**
   * Stops the backend services. Note that this does not stop the
   * ActorSystem.
   */
  def stop(): Unit = {
    logger.info("Convergence Backend Services shutting down.")
    this.convergenceDbProvider.foreach(_.shutdown())
  }


  /**
   * Creates the connection pool to the Convergence database.
   *
   * @param persistenceConfig The config subtree for the persistence subsystem.
   * @return A [[DatabaseProvider]] if the connection pool creation succeeds.
   */
  private[this] def createPool(persistenceConfig: Config): Try[DatabaseProvider] = {
    logger.debug("Creating connection pool to convergence database...")
    val dbServerConfig = persistenceConfig.getConfig("server")

    val baseUri = dbServerConfig.getString("uri")

    val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")
    val convergenceDatabase = convergenceDbConfig.getString("database")
    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")

    val poolMin = convergenceDbConfig.getInt("pool.db-pool-min")
    val poolMax = convergenceDbConfig.getInt("pool.db-pool-max")

    val convergenceDbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password, poolMin, poolMax)
    this.convergenceDbProvider = Some(convergenceDbProvider)

    convergenceDbProvider.connect().map { _ =>
      logger.debug("Connected to convergence database.")
      convergenceDbProvider
    }
  }

  /**
   * Creates and registers the local [[DomainPersistenceManagerActor]] on this
   * node.
   *
   * @param persistenceConfig     The config subtree for the persistence
   *                              subsystem.
   * @param convergenceDbProvider The database provider for the Convergence
   *                              database.
   * @return Success if the actor was started and registered; a failure
   *         otherwise.
   */
  private[this] def createDomainPersistenceManager(persistenceConfig: Config, convergenceDbProvider: DatabaseProvider): Try[Unit] = {
    val domainStore = new DomainStore(convergenceDbProvider)
    val dbServerConfig = persistenceConfig.getConfig("server")
    val baseUri = dbServerConfig.getString("uri")
    val persistenceManager = context.spawn(DomainPersistenceManagerActor(baseUri, domainStore, domainLifecycleTopic), "DomainPersistenceManager")

    implicit val t: Timeout = Timeout(15, TimeUnit.SECONDS)
    implicit val scheduler: Scheduler = context.system.scheduler

    logger.debug("Registering DomainPersistenceManagerActor")
    val f = persistenceManager.ask[DomainPersistenceManagerActor.Registered](DomainPersistenceManagerActor.Register(t, _))

    Try(Await.ready(f, t.duration)).map { _ =>
      logger.debug("DomainPersistenceManagerActor registered")
      ()
    }
  }

  /**
   * Starts the backend services actors.
   *
   * @param persistenceConfig     The config subtree for the persistence
   *                              subsystem.
   * @param convergenceDbProvider The database provider for the Convergence
   *                              database.
   * @return Success if the actors are started; a failure otherwise.
   */
  private[this] def startActors(persistenceConfig: Config, convergenceDbProvider: DatabaseProvider): Try[Unit] = {
    val dbServerConfig = persistenceConfig.getConfig("server")
    val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

    val domainStore = new DomainStore(convergenceDbProvider)
    val userStore = new UserStore(convergenceDbProvider)
    val userApiKeyStore = new UserApiKeyStore(convergenceDbProvider)
    val roleStore = new RoleStore(convergenceDbProvider)
    val configStore = new ConfigStore(convergenceDbProvider)
    val userSessionTokenStore = new UserSessionTokenStore(convergenceDbProvider)
    val namespaceStore = new NamespaceStore(convergenceDbProvider)

    val favoriteDomainStore = new UserFavoriteDomainStore(convergenceDbProvider)
    val deltaHistoryStore: DeltaHistoryStore = new DeltaHistoryStore(convergenceDbProvider)

    val userCreator = new UserCreator(convergenceDbProvider)

    val singletonManager = ClusterSingleton(context.system)

    // This is a cluster singleton that cleans up User Session Tokens after they have expired.
    singletonManager.init(
      SingletonActor(Behaviors.supervise(UserSessionTokenReaperActor(userSessionTokenStore))
        .onFailure[Exception](SupervisorStrategy.restart), "UserSessionTokenReaper")
        .withSettings(ClusterSingletonSettings(context.system).withRole("backed"))
    )

    //
    // REST Services
    //

    // These are Actors that serve up basic low volume Convergence Services such as
    // CRUD for users, roles, authentication, etc. These actors are not sharded.

    // Import, export, and domain / database provisioning
    val domainProvisioner = new DomainProvisioner(convergenceDbProvider, context.system.settings.config)
    val provisionerActor = context.spawn(DomainProvisionerActor(domainProvisioner, domainLifecycleTopic), "DomainProvisioner")

    val databaseManager = new DatabaseManager(dbServerConfig.getString("uri"), convergenceDbProvider, convergenceDbConfig)
    context.spawn(DatabaseManagerActor(databaseManager), "DatabaseManager")

    val domainCreationTimeoutMillis = persistenceConfig.getDuration("domain-databases.initialization-timeout").toMillis
    val domainCreationTimeout = Timeout(domainCreationTimeoutMillis, TimeUnit.MILLISECONDS)

    val domainCreator: DomainCreator = new ActorBasedDomainCreator(
      convergenceDbProvider,
      this.context.system.settings.config,
      provisionerActor.narrow[ProvisionDomain],
      context.executionContext,
      context.system.scheduler,
      domainCreationTimeout)

    val domainStoreActor = context.spawn(DomainStoreActor(
      domainStore, configStore, roleStore, favoriteDomainStore, deltaHistoryStore, domainCreator, provisionerActor), "DomainStore")

    context.spawn(AuthenticationActor(userStore, userApiKeyStore, roleStore, configStore, userSessionTokenStore), "Authentication")
    context.spawn(UserStoreActor(userStore, roleStore, userCreator, domainStoreActor), "UserManager")
    context.spawn(NamespaceStoreActor(namespaceStore, roleStore, configStore), "NamespaceStore")
    context.spawn(RoleStoreActor(roleStore), "RoleStore")
    context.spawn(UserApiKeyStoreActor(userApiKeyStore), "UserApiKeyStore")
    context.spawn(ConfigStoreActor(configStore), "ConfigStore")
    context.spawn(ServerStatusActor(domainStore, namespaceStore), "ServerStatus")
    context.spawn(UserFavoriteDomainStoreActor(favoriteDomainStore), "FavoriteDomains")

    Success(())
  }
}
