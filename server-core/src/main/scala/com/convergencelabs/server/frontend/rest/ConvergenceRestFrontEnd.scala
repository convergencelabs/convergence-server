package com.convergencelabs.server.frontend.rest

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.convergence.AuthenticationActor
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.convergence.DomainStoreActor
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.convergence.RoleStoreActor
import com.convergencelabs.server.db.data.ConvergenceImportService
import com.convergencelabs.server.db.data.ConvergenceImporterActor
import com.convergencelabs.server.db.schema.DatabaseManagerActor
import com.convergencelabs.server.domain.model.RealtimeModelSharding
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
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.Directives.extractUri
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import akka.http.scaladsl.server.directives.SecurityDirectives.authorize
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import grizzled.slf4j.Logging
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.server.AuthorizationFailedRejection
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor
import com.convergencelabs.server.datastore.convergence.UserFavoriteDomainStoreActor

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
      complete(notFoundResponse())

    case e: Exception =>
      extractUri { uri =>
        logger.error(s"Error handling rest call: ${uri}", e)
        complete(InternalServerError)
      }
  }

  def start(): Unit = {
    val masterAdminToken = system.settings.config.getString("convergence.rest.master-admin-api-key")

    val authStoreActor = createRouter("/user/" + AuthenticationActor.RelativePath, "authActor")
    val convergenceUserActor = createRouter("/user/" + ConvergenceUserManagerActor.RelativePath, "convergenceUserActor")
    val namespaceActor = createRouter("/user/" + NamespaceStoreActor.RelativePath, "namespaceActor")
    val databaseManagerActor = createRouter("/user/" + DatabaseManagerActor.RelativePath, "databaseManagerActor")
    val importerActor = createRouter("/user/" + ConvergenceImporterActor.RelativePath, "importerActor")
    val roleActor = createRouter("/user/" + RoleStoreActor.RelativePath, "roleActor")
    val configActor = createRouter("/user/" + ConfigStoreActor.RelativePath, "configActor")
    val favoriteDomainsActor = createRouter("/user/" + UserFavoriteDomainStoreActor.RelativePath, "favoriteDomainsActor")

    
    val authenticator = new Authenticator(authStoreActor, masterAdminToken, defaultRequestTimeout, ec)
    
    // The Rest Services
    val authService = new AuthService(ec, authStoreActor, defaultRequestTimeout)
    val currentUserService = new CurrentUserService(ec, convergenceUserActor, favoriteDomainsActor, defaultRequestTimeout)
    val namespaceService = new NamespaceService(ec, namespaceActor, defaultRequestTimeout)
    val roleService = new RoleService(ec, roleActor, defaultRequestTimeout)
    val configService = new ConfigService(ec, configActor, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)
    val convergenceUserService = new ConvergenceUserService(ec, convergenceUserActor, defaultRequestTimeout)
    val convergenceImportService = new ConvergenceImportService(ec, importerActor, defaultRequestTimeout)
    val databaseManagerService = new DatabaseManagerRestService(ec, databaseManagerActor, defaultRequestTimeout)

    
    // This handles all of the domain specific stuff, we create a router here because each back end node
    // has an instance, we route to them as a group.
    val domainStoreActor = createRouter("/user/" + DomainStoreActor.RelativePath, "domainStoreActor")

    val domainService = new DomainService(
      ec,
      domainStoreActor,
      restDomainActorRegion,
      roleActor,
      modelClusterRegion,
      defaultRequestTimeout)

    implicit def rejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(message, throwable) =>
          complete(ErrorResponse("malformed_request_content", Some(message)))
        case AuthorizationFailedRejection =>
          complete(ForbiddenError)
        //        case e: Any =>
        //          logger.error("An unexpected rejection occured: " + e)
        //          complete(InternalServerError)
      }
      .result()

    val route = cors(ConvergenceRestFrontEnd.ConvergenceCorsSettings) {
      handleExceptions(exceptionHandler) {
        pathPrefix("v1") {
          // Authentication services can be called without being authenticated
          authService.route ~
            // Everything else must be authenticated as a convergence user.
            extractRequest { request =>
              authenticator.requireAuthenticatedUser(request) { authProfile =>
                concat(
                  currentUserService.route(authProfile),
                  convergenceUserService.route(authProfile),
                  namespaceService.route(authProfile),
                  domainService.route(authProfile),
                  roleService.route(authProfile),
                  configService.route(authProfile),
                  keyGenService.route(),
                  convergenceImportService.route(authProfile),
                  databaseManagerService.route(authProfile))
              }
            }
        }
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
    logger.info("Convergence Rest Frontend shutting down.")
    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Rest Frontend shut down.")
    }
  }
}
