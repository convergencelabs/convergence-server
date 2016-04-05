package com.convergencelabs.server.frontend.rest

import scala.io.StdIn
import scala.language.postfixOps
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsDirectives._


import AuthenticateDirectives._

object RestFrontEnd {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    implicit val defaultRequestTimeout = Timeout(2 seconds)

    // Here are the actors that do the actual work
    val authActor = system.actorOf(Props[AuthRestActor])
    val domainActor = system.actorOf(Props[DomainRestActor])

    // These are the rest services
    val domainService = new DomainService(ec, domainActor, defaultRequestTimeout)
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)

    // Set up the route by making everything come under "/rest".  Then we concat all
    // of the routes from each of the services.
    val route = cors() {
      pathPrefix("rest") {
        authService.route ~
          requireAuthenticated { userId =>
            domainService.route
          }
      }
    }

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, "localhost", 8081)

    println(s"Server online at http://localhost:8081/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done
  }
}