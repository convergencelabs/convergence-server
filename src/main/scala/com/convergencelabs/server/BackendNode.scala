package com.convergencelabs.server

import akka.actor.ActorSystem
import com.convergencelabs.server.datastore.PersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainManagerActor
import grizzled.slf4j.Logging

class BackendNode(system: ActorSystem) extends Logging {

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    // FIXME we could pass this in.
    val dbConfig = system.settings.config.getConfig("convergence.database")

    val baseUri = dbConfig.getString("uri")
    val fullUri = baseUri + "/" + dbConfig.getString("database")
    val username = dbConfig.getString("username")
    val password = dbConfig.getString("password")

    val dbPool = new OPartitionedDatabasePool(fullUri, password, password)
    val persistenceProvider = new PersistenceProvider(dbPool)

    // FIXME do we get this from the config.  If so do we need to pass it?
    val protocolConfig = ProtocolConfiguration(5L)

    val dbPoolManager = system.actorOf(
      DomainPersistenceManagerActor.props(
        baseUri,
        persistenceProvider.domainConfigStore),
      DomainPersistenceManagerActor.RelativePath)

    system.actorOf(DomainManagerActor.props(
      persistenceProvider, 
      protocolConfig), 
      DomainManagerActor.RelativeActorPath)

    logger.info("Backend Node started up.")
  }
}