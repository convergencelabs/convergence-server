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


object RestFrontEnd {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("convergence-rest-front-end")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher

    implicit val defaultRequestTimeout = Timeout(2 seconds)
    
    val authenticator = new Authenticator(null, defaultRequestTimeout, ec)

    // Here are the actors that do the actual work
    val authActor = system.actorOf(Props[AuthRestActor])
    val domainActor = system.actorOf(Props[DomainRestActor])

    // These are the rest services
    val domainService = new DomainService(ec, domainActor, defaultRequestTimeout)
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)

    val route = cors() {
      // All request are under the "rest" path.
      pathPrefix("rest") {
        // You can call the auth service without being authenticated
        authService.route ~
          // Everything else must be authenticated
          authenticator.requireAuthenticated { userId =>
            domainService.route(userId)
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