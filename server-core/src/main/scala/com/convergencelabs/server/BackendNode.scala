package com.convergencelabs.server

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.datastore.PersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainManagerActor
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorSystem
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

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    val dbPoolManager = system.actorOf(
      DomainPersistenceManagerActor.props(
        baseUri,
        persistenceProvider.domainStore),
      DomainPersistenceManagerActor.RelativePath)

    system.actorOf(DomainManagerActor.props(
      persistenceProvider,
      protocolConfig),
      DomainManagerActor.RelativeActorPath)

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {

  }
}
