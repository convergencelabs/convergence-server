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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, ClusterSingletonSettings, SingletonActor}
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.convergence.server.db.provision.{DomainLifecycleTopic, DomainProvisioner, DomainProvisionerActor}
import com.convergencelabs.convergence.server.db.schema.{DatabaseManager, DatabaseManagerActor}
import grizzled.slf4j.Logging

import scala.language.postfixOps

/**
 * The [[BackendServices]] class is the main entry point that bootstraps the
 * core business logic services in the Convergence Server. It is responsible
 * for start that various Akka Actors the comprise the major subsystems (
 * Chat, Presence, Models, etc.).
 *
 * @param context               The Akka ActorContext to start Actors in.
 * @param convergenceDbProvider A [[DatabaseProvider]] that is connected to the
 *                              main convergence database.
 */
class BackendServices(context: ActorContext[_],
                      convergenceDbProvider: DatabaseProvider,
                      domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]
                     ) extends Logging {

  /**
   * Starts the Backend Services. Largely this method will start up all
   * of the Actors required to provide e the core Convergence Server
   * services.
   */
  def start(): Unit = {
    logger.info("Convergence Backend Services starting up...")

    val dbServerConfig = context.system.settings.config.getConfig("convergence.persistence.server")
    val convergenceDbConfig = context.system.settings.config.getConfig("convergence.persistence.convergence-database")

    val userStore = new UserStore(convergenceDbProvider)
    val userApiKeyStore = new UserApiKeyStore(convergenceDbProvider)
    val roleStore = new RoleStore(convergenceDbProvider)
    val configStore = new ConfigStore(convergenceDbProvider)
    val userSessionTokenStore = new UserSessionTokenStore(convergenceDbProvider)
    val namespaceStore = new NamespaceStore(convergenceDbProvider)
    val domainStore = new DomainStore(convergenceDbProvider)

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

    val domainCreator: DomainCreator = new ActorBasedDomainCreator(
      convergenceDbProvider,
      this.context.system.settings.config,
      provisionerActor.narrow[ProvisionDomain],
      context.executionContext,
      context.system.scheduler)

    val domainStoreActor = context.spawn(DomainStoreActor(
      domainStore, configStore, roleStore, favoriteDomainStore, deltaHistoryStore, domainCreator, provisionerActor), "DomainStore")
    // FIXME enable importer
    //    context.spawn(ConvergenceImporterActor.props(
    //      dbServerConfig.getString("uri"),
    //      convergenceDbProvider,
    //      domainStoreActor),"ConvergenceImporter")


    context.spawn(AuthenticationActor(userStore, userApiKeyStore, roleStore, configStore, userSessionTokenStore), "Authentication")
    context.spawn(ConvergenceUserManagerActor(userStore, roleStore, userCreator, domainStoreActor), "UserManager")
    context.spawn(NamespaceStoreActor(namespaceStore, roleStore, configStore), "NamespaceStore")
    context.spawn(RoleStoreActor(roleStore), "RoleStore")
    context.spawn(UserApiKeyStoreActor(userApiKeyStore), "UserApiKeyStore")
    context.spawn(ConfigStoreActor(configStore), "ConfigStore")
    context.spawn(ServerStatusActor(domainStore, namespaceStore), "ServerStatus")
    context.spawn(UserFavoriteDomainStoreActor(favoriteDomainStore), "FavoriteDomains")

    logger.info("Convergence Backend Services started up.")
  }

  /**
   * Stops the backend services. Note that this does not stop the
   * ActorSystem.
   */
  def stop(): Unit = {
    logger.info("Convergence Backend Services shutting down.")
  }
}
