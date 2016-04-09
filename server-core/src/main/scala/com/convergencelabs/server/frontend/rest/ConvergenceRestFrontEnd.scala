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
import com.convergencelabs.server.datastore.AuthStoreActor
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.DomainStoreActor
import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.RestDomainManagerActor

class ConvergenceRestFrontEnd(
  val system: ActorSystem,
  val interface: String,
  val port: Int)
    extends Logging {

  implicit val s = system
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val defaultRequestTimeout = Timeout(2 seconds)

  def start(): Unit = {

    // FIXME we could pass this in.
    val dbConfig = system.settings.config.getConfig("convergence.database")

    val baseUri = dbConfig.getString("uri")
    val fullUri = baseUri + "/" + dbConfig.getString("database")
    val username = dbConfig.getString("username")
    val password = dbConfig.getString("password")

    val dbPool = new OPartitionedDatabasePool(fullUri, password, password)

    val authActor = system.actorOf(AuthStoreActor.props(dbPool))
    val domainActor = system.actorOf(DomainStoreActor.props(dbPool))
    val domainManagerActor = system.actorOf(RestDomainManagerActor.props(dbPool))

    // These are the rest services
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)
    val authenticator = new Authenticator(authActor, defaultRequestTimeout, ec)
    val domainService = new DomainService(ec, domainActor, domainManagerActor, defaultRequestTimeout)

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
    val bindingFuture = Http().bindAndHandle(route, interface, port)

    logger.info(s"Convergence Rest Front End listening at http://${interface}:${port}/")
  }
}