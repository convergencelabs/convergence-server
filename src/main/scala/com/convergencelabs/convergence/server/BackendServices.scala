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
import com.convergencelabs.convergence.server.db.provision.{DomainLifecycleTopic, DomainProvisioner, DomainProvisionerActor}
import com.convergencelabs.convergence.server.db.schema.{DatabaseManager, DatabaseManagerActor}
import com.convergencelabs.convergence.server.domain.DomainActor
import com.convergencelabs.convergence.server.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.domain.chat.ChatActor
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import grizzled.slf4j.Logging

import scala.language.postfixOps

/**
 * The [[BackendServices]] class is the main entry point that bootstraps the
 * core business logic services in the Convergence Server. It is responsible
 * for start that various Akka Actors the comprise the major subsystems (
 * Chat, Presence, Models, etc.).
 *
 * @param context               The Akka ActorContext to start Actors in.
 * @param convergenceDbProvider A [[com.convergencelabs.convergence.server.db.DatabaseProvider]] that is connected to the
 *                              main convergence database.
 */
class BackendServices(context: ActorContext[_],
                      convergenceDbProvider: DatabaseProvider,
                      activityShardRegion: ActorRef[ActivityActor.Message],
                      chatChannelRegion: ActorRef[ChatActor.Message],
                      domainRegion: ActorRef[DomainActor.Message],
                      realtimeModelRegion: ActorRef[RealtimeModelActor.Message],
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

    val singletonManager = ClusterSingleton(context.system)

    // This is a cluster singleton that cleans up User Session Tokens after they have expired.
    singletonManager.init(
      SingletonActor(Behaviors.supervise(UserSessionTokenReaperActor(convergenceDbProvider))
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

    val domainStoreActor = context.spawn(DomainStoreActor(convergenceDbProvider, provisionerActor), "DomainStore")
    // FIXME enable importer
    //    context.spawn(ConvergenceImporterActor.props(
    //      dbServerConfig.getString("uri"),
    //      convergenceDbProvider,
    //      domainStoreActor),"ConvergenceImporter")

    val userStore = new UserStore(convergenceDbProvider)
    val userApiKeyStore = new UserApiKeyStore(convergenceDbProvider)
    val roleStore = new RoleStore(convergenceDbProvider)
    val configStore = new ConfigStore(convergenceDbProvider)
    val userSessionTokenStore = new UserSessionTokenStore(convergenceDbProvider)

    context.spawn(AuthenticationActor(userStore, userApiKeyStore, roleStore, configStore, userSessionTokenStore), "Authentication")
    context.spawn(ConvergenceUserManagerActor(convergenceDbProvider, domainStoreActor), "UserManager")
    context.spawn(NamespaceStoreActor(convergenceDbProvider), "NamespaceStore")
    context.spawn(RoleStoreActor(convergenceDbProvider), "RoleStore")
    context.spawn(UserApiKeyStoreActor(convergenceDbProvider), "UserApiKeyStore")
    context.spawn(ConfigStoreActor(convergenceDbProvider), "ConfigStore")
    context.spawn(ServerStatusActor(convergenceDbProvider), "ServerStatus")
    context.spawn(UserFavoriteDomainStoreActor(convergenceDbProvider), "FavoriteDomains")

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
