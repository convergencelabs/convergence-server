package com.convergencelabs.server.frontend.realtime.http

import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

object DomainFlowFactory {
  def apply(system: ActorSystem): DomainFlowFactory = {
    new DomainFlowFactory(system)
  }
}

case class IncomingMessage(message: String)
case class OutgoingMessage(message: String)

class DomainFlowFactory(val system: ActorSystem) {

  def createFlowForConnection(namespace: String, domain: String): Flow[IncomingMessage, OutgoingMessage, Any] = {

    val connection = system.actorOf(Props[ConnectionActor])

    // This is how we route messages that are coming in.  Basically we route them
    // to the echo actor and, when the flow is completed (e.g. the web socket is
    // disconnected) we send a Disconnected case class.
    val in = Flow[IncomingMessage].to(Sink.actorRef[IncomingMessage](connection, Disconnected()))

    // This is where outgoing messages will go.  Basically we create an actor based
    // source for messages.  This creates an actor ref that you can send messages to
    // and then will be spit out the flow.  However to get access to this you must
    // materialize the source.  By materializing it we get a reference to the underlying
    // actor and we can send another actor this reference, or use the reference however
    // we want (e.g. create a connection)
    val out = Source.actorRef[OutgoingMessage](1, OverflowStrategy.fail).mapMaterializedValue({ ref =>
      connection ! Connected(ref)
    })

    println(s"connection for $namespace / $domain")

    Flow.fromSinkAndSource(in, out)
  }
}
