package com.convergencelabs.server

import scala.language.postfixOps

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import grizzled.slf4j.Logging

class BackendNode(system: ActorSystem, dbProvider: DatabaseProvider) extends Logging {

  var actor: Option[ActorRef] = None
  
  def start(): Unit = {
    logger.info("Backend Node starting up.")

    val dbConfig = system.settings.config.getConfig("convergence.convergence-database")

    val domainStore = new DomainStore(dbProvider)

    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

    actor = Some(system.actorOf(BackendNodeActor.props(
      domainStore,
      protocolConfig,
      DomainPersistenceManagerActor),
      BackendNodeActor.RelativeActorPath))

    logger.info("Backend Node started up.")
  }

  def stop(): Unit = {
    actor.foreach(_ ! PoisonPill)
  }
}
