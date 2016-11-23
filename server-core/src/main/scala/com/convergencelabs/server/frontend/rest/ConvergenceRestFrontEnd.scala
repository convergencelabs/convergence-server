package com.convergencelabs.server.frontend.rest

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.datastore.AuthStoreActor
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.DomainStoreActor
import com.convergencelabs.server.datastore.RegistrationActor
import com.convergencelabs.server.domain.RestAuthnorizationActor
import com.convergencelabs.server.domain.RestDomainManagerActor
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsDirectives.cors
import ch.megard.akka.http.cors.CorsSettings
import grizzled.slf4j.Logging
import com.convergencelabs.server.db.provision.DomainProvisioner
import com.convergencelabs.server.db.provision.DomainProvisionerActor
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.db.schema.DatabaseManager
import com.convergencelabs.server.db.schema.DatabaseManagerActor

object ConvergenceRestFrontEnd {
  val ConvergenceCorsSettings = CorsSettings.defaultSettings.copy(
    allowedMethods = List(
      HttpMethods.GET,
      HttpMethods.POST,
      HttpMethods.PUT,
      HttpMethods.DELETE,
      HttpMethods.HEAD,
      HttpMethods.OPTIONS))
}

class ConvergenceRestFrontEnd(
  val system: ActorSystem,
  val interface: String,
  val port: Int,
  val dbPool: OPartitionedDatabasePool)
    extends Logging {

  implicit val s = system
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val defaultRequestTimeout = Timeout(20 seconds)

  def start(): Unit = {
    // FIXME this is a hack all of this should be a rest backend
    val orientDbConfig = system.settings.config.getConfig("convergence.orient-db")
    val domainProvisioner = new DomainProvisioner(
      orientDbConfig.getString("db-uri"),
      orientDbConfig.getString("admin-username"),
      orientDbConfig.getString("admin-password"))
    val provisionerActor = system.actorOf(DomainProvisionerActor.props(domainProvisioner), DomainProvisionerActor.RelativePath)

    val domainActor = system.actorOf(DomainStoreActor.props(dbPool, provisionerActor))
    
    val importerActor = system.actorOf(ConvergenceImporterActor.props(
      orientDbConfig.getString("db-uri"),
      dbPool,
      domainActor), ConvergenceImporterActor.RelativePath)

    val authActor = system.actorOf(AuthStoreActor.props(dbPool))
    val userManagerActor = system.actorOf(ConvergenceUserManagerActor.props(dbPool, domainActor))
    val registrationActor = system.actorOf(RegistrationActor.props(dbPool, userManagerActor))
    val domainManagerActor = system.actorOf(RestDomainManagerActor.props(dbPool))

    // FIXME should this take an actor ref instead?
    val domainStore = new DomainStore(dbPool)
    val authzActor = system.actorOf(RestAuthnorizationActor.props(domainStore))
    val convergenceUserActor = system.actorOf(ConvergenceUserManagerActor.props(dbPool, domainActor))
    
    val databaseManager = new DatabaseManager(orientDbConfig.getString("db-uri"), dbPool)
    val databaseManagerActor = system.actorOf(DatabaseManagerActor.props(databaseManager))

    // Down to here

    val registrationBaseUrl = system.settings.config.getString("convergence.registration-base-url")

    // These are the rest services
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)
    val authenticator = new Authenticator(authActor, defaultRequestTimeout, ec)
    val registrationService = new RegistrationService(ec, registrationActor, defaultRequestTimeout, registrationBaseUrl)
    val domainService = new DomainService(ec, authzActor, domainActor, domainManagerActor, defaultRequestTimeout)
    val profileService = new ProfileService(ec, convergenceUserActor, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)
    val convergenceUserService = new ConvergenceUserService(ec, convergenceUserActor, defaultRequestTimeout)

    val convergenceImportService = new ConvergenceImportService(ec, importerActor, defaultRequestTimeout)
    
    val databaseManagerService = new DatabaseManagerRestService(ec, databaseManagerActor, defaultRequestTimeout)

    val adminsConfig = system.settings.config.getConfig("convergence.convergence-admins")

    val route = cors(ConvergenceRestFrontEnd.ConvergenceCorsSettings) {
      // All request are under the "rest" path.
      pathPrefix("rest") {
        // You can call the auth service without being authenticated
        authService.route ~
          // Everything else must be authenticated
          extractRequest { request =>
            authenticator.requireAuthenticated(request) { username =>
              domainService.route(username) ~
                keyGenService.route() ~
                profileService.route(username)
            }
          }
      } ~ pathPrefix("admin") {
        authenticateBasic(realm = "convergence admin", AdminAuthenticator.authenticate(adminsConfig)) { adminUser =>
          convergenceUserService.route(adminUser) ~
            convergenceImportService.route(adminUser) ~
            databaseManagerService.route(adminUser)
        }
      } ~ registrationService.route
    }

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, interface, port)
    logger.info(s"Convergence Rest Front End listening at http://${interface}:${port}/")
  }
}
