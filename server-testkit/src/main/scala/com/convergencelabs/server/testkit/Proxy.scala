package com.convergencelabs.server.testkit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{ Sink, Source }
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import scala.util.Success
import scala.util.Failure

object Proxy extends App {

  implicit val system = ActorSystem("Proxy")

  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  val port = 8081

  val proxy: Route = { context =>
    val request = context.request
    println("Opening connection to " + request.uri.authority.host.address)
    val flow = Http(system).outgoingConnection(request.uri.authority.host.address, 8080)
    Source.single(context.request)
      .via(flow)
      .runWith(Sink.head)
      .flatMap(r => context.complete(r))
  }

  val binding = Http(system).bindAndHandle(proxy, interface = "localhost", port = port)
  binding.onComplete {
    case Success(binding) ⇒
      val localAddress = binding.localAddress
      println(s"Proxy started up on port $port.")
    case Failure(e) ⇒
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }
}