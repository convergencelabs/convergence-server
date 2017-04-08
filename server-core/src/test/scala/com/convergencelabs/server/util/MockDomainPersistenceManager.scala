package com.convergencelabs.server.util

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorContext
import akka.actor.ActorRef

class MockDomainPersistenceManager(val mockProviders: Map[DomainFqn, DomainPersistenceProvider]) extends DomainPersistenceManager {

  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Try[DomainPersistenceProvider] = {
    mockProviders.get(domainFqn) match {
        case Some(provider) => Success(provider)
        case None => Failure(new IllegalArgumentException(s"Don't have provider for domain ${domainFqn}"))
      }
  }
  
  def releasePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Unit = {
    
  }
}
