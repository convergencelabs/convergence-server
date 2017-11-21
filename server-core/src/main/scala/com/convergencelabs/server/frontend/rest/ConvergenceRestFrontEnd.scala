package com.convergencelabs.server.frontend.rest

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.AuthStoreActor
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DomainStoreActor
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.PermissionsStoreActor
import com.convergencelabs.server.datastore.RegistrationActor
import com.convergencelabs.server.db.data.ConvergenceImportService
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.db.schema.DatabaseManagerActor
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.AuthorizationActor
import com.convergencelabs.server.domain.rest.RestDomainActorSharding

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.routing.ClusterRouterGroup
import akka.cluster.routing.ClusterRouterGroupSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.Directives.extractUri
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import grizzled.slf4j.Logging

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
  val port: Int)
    extends Logging with JsonSupport {

  implicit val s = system
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val defaultRequestTimeout = Timeout(20 seconds)

  var binding: Option[Http.ServerBinding] = None

  private[this] val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(system)
  private[this] val restDomainActorRegion: ActorRef = RestDomainActorSharding.shardRegion(system)

  val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: DuplicateValueException =>
      complete(duplicateResponse(e.field))

    case e: InvalidValueExcpetion =>
      complete(invalidValueResponse(e.field))

    case e: EntityNotFoundException =>
      complete(NotFoundError)

    case e: Exception =>
      extractUri { uri =>
        logger.error(s"Error handling rest call: ${uri}", e)
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "There was an internal server error."))
      }
  }

  def start(): Unit = {

    val registrationBaseUrl = system.settings.config.getString("convergence.registration-base-url")

    val authStoreActor = createRouter("/user/" + AuthStoreActor.RelativePath, "authStoreActor")
    val convergenceUserActor = createRouter("/user/" + ConvergenceUserManagerActor.RelativePath, "convergenceUserActor")
    val registrationActor = createRouter("/user/" + RegistrationActor.RelativePath, "registrationActor")
    val databaseManagerActor = createRouter("/user/" + DatabaseManagerActor.RelativePath, "databaseManagerActor")
    val importerActor = createRouter("/user/" + ConvergenceImporterActor.RelativePath, "importerActor")
    val authorizationActor = createRouter("/user/" + AuthorizationActor.RelativePath, "authorizationActor")
    val permissionStoreActor = createRouter("/user/" + PermissionsStoreActor.RelativePath, "permissionStoreActor")

    // The Rest Services

    // All of these services are global to the system and outside of the domain.
    val authService = new AuthService(ec, authStoreActor, defaultRequestTimeout)
    val authenticator = new Authenticator(authStoreActor, defaultRequestTimeout, ec)
    val registrationService = new RegistrationService(ec, registrationActor, defaultRequestTimeout, registrationBaseUrl)
    val profileService = new ProfileService(ec, convergenceUserActor, defaultRequestTimeout)
    val passwordService = new PasswordService(ec, convergenceUserActor, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)

    val convergenceUserAdminService = new ConvergenceUserAdminService(ec, convergenceUserActor, defaultRequestTimeout)
    val convergenceUserService = new ConvergenceUserService(ec, convergenceUserActor, defaultRequestTimeout)
    val convergenceImportService = new ConvergenceImportService(ec, importerActor, defaultRequestTimeout)
    val databaseManagerService = new DatabaseManagerRestService(ec, databaseManagerActor, defaultRequestTimeout)

    // This handles all of the domains specific stuff
    val domainStoreActor = createRouter("/user/" + DomainStoreActor.RelativePath, "domainStoreActor")
    
    val domainService = new DomainService(ec, 
        authorizationActor, 
        domainStoreActor,
        restDomainActorRegion, 
        permissionStoreActor, 
        modelClusterRegion, 
        defaultRequestTimeout)

    val adminsConfig = system.settings.config.getConfig("convergence.convergence-admins")

    val route = cors(ConvergenceRestFrontEnd.ConvergenceCorsSettings) {
      handleExceptions(exceptionHandler) {
        // All request are under the "rest" path.
        pathPrefix("rest") {
          // You can call the auth service without being authenticated
          authService.route ~
            // Everything else must be authenticated
            extractRequest { request =>
              authenticator.requireAuthenticated(request) { username =>
                domainService.route(username) ~
                  keyGenService.route() ~
                  profileService.route(username) ~
                  passwordService.route(username) ~
                  convergenceUserService.route(username)
              }
            }
        } ~ pathPrefix("admin") {
          authenticateBasic(realm = "convergence admin", AdminAuthenticator.authenticate(adminsConfig)) { adminUser =>
            convergenceUserAdminService.route(adminUser) ~
              convergenceImportService.route(adminUser) ~
              databaseManagerService.route(adminUser)
          }
        } ~ registrationService.route
      }
    }

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, interface, port).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        val localAddress = b.localAddress
        logger.info(s"Rest Front End started up on port http://${interface}:${port}.")
      case Failure(e) ⇒
        logger.info(s"Rest Front End Binding failed with ${e.getMessage}")
        system.terminate()
    }
  }

  def createRouter(path: String, name: String): ActorRef = {
    system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = 100, routeesPaths = List(path),
          allowLocalRoutees = true, useRoles = Set("backend"))).props(),
      name = name)
  }

  def stop(): Unit = {
    this.binding foreach { b => b.unbind() }
  }
}
