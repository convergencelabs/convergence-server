package com.convergencelabs.server.frontend.rest

import akka.actor.Actor
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.actor.ActorRef

case object DomainsRequest
case class DomainFqn(namespace: String, domain: String)

case class DomainRequest(domainId: String)
case class DomainInfo(namespace: String, domain: String, title: String)

class DomainRestActor extends Actor {

  def receive = {
    case DomainsRequest =>
      sender ! List(DomainFqn("ns1", "domainId1"), DomainFqn("ns1", "domainId2"))
    case DomainRequest(domainId) =>
      if (domainId == "1") {
        sender ! Some(DomainInfo("ns", "1", "A Title"))
      } else {
        sender ! None
      }
  }

}