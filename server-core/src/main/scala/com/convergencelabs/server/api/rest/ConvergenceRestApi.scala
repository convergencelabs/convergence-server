package com.convergencelabs.server.api.rest

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.InvalidValueExcpetion
import com.convergencelabs.server.datastore.convergence.AuthenticationActor
import com.convergencelabs.server.datastore.convergence.ConfigStoreActor
import com.convergencelabs.server.datastore.convergence.ConvergenceUserManagerActor
import com.convergencelabs.server.datastore.convergence.DomainStoreActor
import com.convergencelabs.server.datastore.convergence.NamespaceStoreActor
import com.convergencelabs.server.datastore.convergence.RoleStoreActor
import com.convergencelabs.server.datastore.convergence.UserFavoriteDomainStoreActor
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
import akka.http.scaladsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.Directives.extractUri
import akka.http.scaladsl.server.Directives.handleExceptions
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import grizzled.slf4j.Logging
import com.convergencelabs.server.api.rest.domain.DomainService
import com.convergencelabs.server.domain.chat.ChatSharding

object ConvergenceRestApi {
  val ConvergenceCorsSettings = CorsSettings.defaultSettings.copy(
    allowedMethods = List(
      HttpMethods.GET,
      HttpMethods.POST,
      HttpMethods.PUT,
      HttpMethods.DELETE,
      HttpMethods.HEAD,
      HttpMethods.OPTIONS))
}

class ConvergenceRestApi(
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
  private[this] val chatActorRegion: ActorRef = ChatSharding.shardRegion(system)

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
    val authStoreActor = createBackendRouter(AuthenticationActor.RelativePath, "authActor")
    val convergenceUserActor = createBackendRouter(ConvergenceUserManagerActor.RelativePath, "convergenceUserActor")
    val namespaceActor = createBackendRouter(NamespaceStoreActor.RelativePath, "namespaceActor")
    val databaseManagerActor = createBackendRouter(DatabaseManagerActor.RelativePath, "databaseManagerActor")
    val importerActor = createBackendRouter(ConvergenceImporterActor.RelativePath, "importerActor")
    val roleActor = createBackendRouter(RoleStoreActor.RelativePath, "roleActor")
    val configActor = createBackendRouter(ConfigStoreActor.RelativePath, "configActor")
    val favoriteDomainsActor = createBackendRouter(UserFavoriteDomainStoreActor.RelativePath, "favoriteDomainsActor")
    val domainStoreActor = createBackendRouter(DomainStoreActor.RelativePath, "domainStoreActor")

    val authenticator = new Authenticator(authStoreActor, defaultRequestTimeout, ec)

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

    val domainService = new DomainService(
      ec,
      domainStoreActor,
      restDomainActorRegion,
      roleActor,
      modelClusterRegion,
      chatActorRegion,
      defaultRequestTimeout)

    implicit def rejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(message, throwable) =>
          complete(ErrorResponse("malformed_request_content", Some(message)))
        case AuthorizationFailedRejection =>
          complete(ForbiddenError)
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(methodNotAllowed(names))
      }
      .handleNotFound {
        complete(notFoundResponse(Some("The requested resoruce could not be found.")))
      }
      .result()

    val route = cors(ConvergenceRestApi.ConvergenceCorsSettings) {
      handleExceptions(exceptionHandler) {
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

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, interface, port).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        val localAddress = b.localAddress
        logger.info(s"Rest API started at: http://${interface}:${port}")
      case Failure(e) ⇒
        logger.info(s"Rest API binding failed with ${e.getMessage}")
        system.terminate()
    }
  }

  def createBackendRouter(relativePath: String, localName: String): ActorRef = {
    system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = 100, routeesPaths = List("/user/" + relativePath),
          allowLocalRoutees = true, useRoles = Set("backend"))).props(),
      name = localName)
  }

  def stop(): Unit = {
    logger.info("Convergence Rest API shutting down.")
    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Rest API shut down.")
    }
  }
}
