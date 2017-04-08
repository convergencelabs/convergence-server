package com.convergencelabs.server

import scala.language.postfixOps

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.domain.DomainManagerActor

import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor

class BackendNode(system: ActorSystem, dbProvider: DatabaseProvider) extends Logging {

  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val dbConfig = system.settings.config.getConfig("convergence.convergence-database")

    val domainStore = new DomainStore(dbProvider)

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    system.actorOf(DomainManagerActor.props(
      domainStore,
      protocolConfig,
      DomainPersistenceManagerActor),
      DomainManagerActor.RelativeActorPath)

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {

  }
}
