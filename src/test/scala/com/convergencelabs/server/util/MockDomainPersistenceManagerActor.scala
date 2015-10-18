package com.convergencelabs.server.util

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import akka.actor.Actor
import com.convergencelabs.server.datastore.domain.AcquireDomainPersistence
import com.convergencelabs.server.datastore.domain.PersistenceProviderReference
import com.convergencelabs.server.datastore.domain.PersistenceProviderUnavailable
import akka.actor.Props
import akka.actor.ActorRef
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import akka.testkit.TestActorRef
import akka.actor.Nobody
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await

object MockDomainPersistenceManagerActor {
  def apply(system: ActorSystem): TestActorRef[MockDomainPersistenceManagerActor] = {
    val f = system.actorSelection("user").resolveOne(FiniteDuration(1, TimeUnit.SECONDS))
    val ref = Await.result(f, FiniteDuration(1, TimeUnit.SECONDS))
    new TestActorRef[MockDomainPersistenceManagerActor](
        system,
        Props(new MockDomainPersistenceManagerActor()),
        ref,
        DomainPersistenceManagerActor.RelativePath
        )
  }
}

class MockDomainPersistenceManagerActor() extends Actor {
  
  var mockProviders: Map[DomainFqn, DomainPersistenceProvider] = Map()
  
  def receive = {
    case AcquireDomainPersistence(domainFqn, requestor) => {
      mockProviders.get(domainFqn) match {
        case Some(provider) => sender ! PersistenceProviderReference(provider)
        case None => sender ! PersistenceProviderUnavailable
      }
    }
    case _ => {}
  }
}