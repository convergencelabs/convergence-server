package com.convergencelabs.server.frontend.realtime.http

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.http.scaladsl.model.ws.TextMessage
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

trait Echo {
  def echoFlow(): Flow[String, String, Any]
}

case class Disconnected()
case class Connected(ref: ActorRef)

object Echo {
  def create(system: ActorSystem): Echo = {
    new Echo {
      // This method is call is called each time a new connection comes in.
      // at the moment we create a new actor that will handle each connection.
      def echoFlow(): Flow[String, String, Any] = {

        val echoActor = system.actorOf(Props(new Actor {
          // When the actor is created, when the flow is being created, we 
          // don't actually know how to talk to the actor that represents
          // the connection.
          var sourceRef: Option[ActorRef] = None

          def receive: Receive = {
            case message: String ⇒
              println(s"got: ${message} from ${sender}")
              if (message != "ignore") {
                dispatch("Echo: " + message)
              }

            case Connected(ref) =>
              println(s"connected: ${ref}")
              sourceRef = Some(ref)
              implicit val ec = system.dispatcher
              system.scheduler.schedule(
                  FiniteDuration(2, TimeUnit.SECONDS), 
                  FiniteDuration(2, TimeUnit.SECONDS)) {
                sourceRef.get ! "ping"
              }

            case Disconnected() =>
              println(s"cleanly disconnected: ${sourceRef}")
              sourceRef = None
              this.context.stop(this.self)

            case akka.actor.Status.Failure =>
              println(s"uncleanly disconnected: ${sourceRef}")
              sourceRef = None
              this.context.stop(this.self)
          }

          def dispatch(msg: String): Unit = sourceRef.get ! msg
        }))

        // This is how we route messages that are coming in.  Basically we route them
        // to the echo actor and, when the flow is completed (e.g. the web socket is
        // disconnected) we send a Disconnected case class.
        val in = Flow[String].to(Sink.actorRef[String](echoActor, Disconnected()))

        // This is where outgoing messages will go.  Basically we create an actor based
        // source for messages.  This creates an actor ref that you can send messages to
        // and then will be spit out the flow.  However to get access to this you must
        // materialize the source.  By materializing it we get a reference to the underlying
        // actor and we can send another actor this reference, or use the reference however
        // we want (e.g. create a connection)
        val out = Source.actorRef[String](1, OverflowStrategy.fail).mapMaterializedValue({ ref =>
          echoActor ! Connected(ref)
        })

        Flow.fromSinkAndSource(in, out)
      }
    }
  }
}
