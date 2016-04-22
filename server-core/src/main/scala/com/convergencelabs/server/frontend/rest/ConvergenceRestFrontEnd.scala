package com.convergencelabs.server.frontend.rest

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.datastore.AuthStoreActor
import com.convergencelabs.server.datastore.DomainStoreActor
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.RestDomainManagerActor
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsDirectives.cors
import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.RestAuthnorizationActor
import ch.megard.akka.http.cors.CorsSettings
import akka.http.javadsl.model.headers.HttpOrigin
import akka.http.scaladsl.model.headers.HttpOriginRange
import ch.megard.akka.http.cors.HttpHeaderRange
import akka.http.scaladsl.model.HttpMethods

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
    // FIXME this is a hack all of this should be a rest backend
    val dbConfig = system.settings.config.getConfig("convergence.convergence-database")

    val baseUri = dbConfig.getString("uri")
    val fullUri = baseUri + "/" + dbConfig.getString("database")
    val username = dbConfig.getString("username")
    val password = dbConfig.getString("password")

    val dbPool = new OPartitionedDatabasePool(fullUri, password, password)
    val domainStore = new DomainStore(dbPool)

    val authActor = system.actorOf(AuthStoreActor.props(dbPool))
    val domainActor = system.actorOf(DomainStoreActor.props(dbPool))
    val domainManagerActor = system.actorOf(RestDomainManagerActor.props(dbPool))
    val authzActor = system.actorOf(RestAuthnorizationActor.props(domainStore))

    val dbPoolManager = system.actorOf(
      DomainPersistenceManagerActor.props(
        baseUri,
        domainStore),
      DomainPersistenceManagerActor.RelativePath)
    // Down to here

    // These are the rest services
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)
    val authenticator = new Authenticator(authActor, defaultRequestTimeout, ec)
    val domainService = new DomainService(ec, authzActor, domainActor, domainManagerActor, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)

    val settings = CorsSettings.defaultSettings.copy(
      allowedMethods = List(
        HttpMethods.GET,
        HttpMethods.POST,
        HttpMethods.PUT,
        HttpMethods.DELETE,
        HttpMethods.HEAD,
        HttpMethods.OPTIONS))

    val route = cors(settings) {
      // All request are under the "rest" path.
      pathPrefix("rest") {
        // You can call the auth service without being authenticated
        authService.route ~
          // Everything else must be authenticated
          extractRequest { request =>
            authenticator.requireAuthenticated(request) { userId =>
              domainService.route(userId) ~
                keyGenService.route()
            }
          }
      }
    }

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, interface, port)
    logger.info(s"Convergence Rest Front End listening at http://${interface}:${port}/")
  }
}
