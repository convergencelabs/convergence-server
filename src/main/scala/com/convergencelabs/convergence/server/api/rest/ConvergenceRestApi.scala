/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, complete, concat, extractRequest, extractUri, handleExceptions}
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server._
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.convergencelabs.convergence.server.api.rest.domain.DomainService
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException, InvalidValueExcpetion}
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor
import com.convergencelabs.convergence.server.db.schema.DatabaseManagerActor
import com.convergencelabs.convergence.server.domain.chat.ChatSharding
import com.convergencelabs.convergence.server.domain.model.RealtimeModelSharding
import com.convergencelabs.convergence.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.convergence.server.util.AkkaRouterUtils._
import grizzled.slf4j.Logging

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ConvergenceRestApi]] class is the main entry point to the Convergence
 * Server REST Subsystem.
 *
 * @param system    The Akka ActorSystem to deploy actors into.
 * @param interface The network interface to bind to.
 * @param port      The network port to bind to.
 */
class ConvergenceRestApi(private[this] val system: ActorSystem,
                         private[this] val interface: String,
                         private[this] val port: Int)
  extends Logging with JsonSupport {

  private[this] implicit val s: ActorSystem = system
  private[this] implicit val ec: ExecutionContextExecutor = system.dispatcher
  private[this] implicit val defaultRequestTimeout: Timeout = Timeout(20 seconds)

  private[this] val routers = ListBuffer[ActorRef]()
  private[this] var binding: Option[Http.ServerBinding] = None

  private[this] val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: DuplicateValueException =>
      complete(duplicateResponse(e.field))

    case e: InvalidValueExcpetion =>
      complete(invalidValueResponse(e.message, Some(e.field)))

    case _: EntityNotFoundException =>
      complete(notFoundResponse())

    case e: Exception =>
      extractUri { uri =>
        logger.error(s"Error handling REST call: $uri", e)
        complete(InternalServerError)
      }
  }

  /**
   * Starts the REST API by deploying the relevant Actor's and binding
   * to the specified interface and port.
   */
  def start(): Unit = {
    // Shard regions we will use. This does not start the shard regions / proxies,
    // it assumes that they are already running.
    val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(system)
    val restDomainActorRegion: ActorRef = RestDomainActorSharding.shardRegion(system)
    val chatActorRegion: ActorRef = ChatSharding.shardRegion(system)

    // Routers to backend services. We add them to a list so we can shut them down.
    val authStoreActor = createBackendRouter(system, AuthenticationActor.RelativePath, "authActor")
    routers += authStoreActor
    val convergenceUserActor = createBackendRouter(system, ConvergenceUserManagerActor.RelativePath, "convergenceUserActor")
    routers += convergenceUserActor
    val namespaceActor = createBackendRouter(system, NamespaceStoreActor.RelativePath, "namespaceActor")
    routers += namespaceActor
    val databaseManagerActor = createBackendRouter(system, DatabaseManagerActor.RelativePath, "databaseManagerActor")
    routers += databaseManagerActor
    val importerActor = createBackendRouter(system, ConvergenceImporterActor.RelativePath, "importerActor")
    routers += importerActor
    val roleActor = createBackendRouter(system, RoleStoreActor.RelativePath, "roleActor")
    routers += roleActor
    val userApiKeyActor = createBackendRouter(system, UserApiKeyStoreActor.RelativePath, "userApiKeyActor")
    routers += userApiKeyActor
    val configActor = createBackendRouter(system, ConfigStoreActor.RelativePath, "configActor")
    routers += configActor
    val favoriteDomainsActor = createBackendRouter(system, UserFavoriteDomainStoreActor.RelativePath, "favoriteDomainsActor")
    routers += favoriteDomainsActor
    val domainStoreActor = createBackendRouter(system, DomainStoreActor.RelativePath, "domainStoreActor")
    routers += domainStoreActor
    val statusActor = createBackendRouter(system, ServerStatusActor.RelativePath, "serverStatusActor")
    routers += statusActor

    // The Rest Services
    val authService = new AuthService(ec, authStoreActor, defaultRequestTimeout)
    val currentUserService = new CurrentUserService(ec, convergenceUserActor, favoriteDomainsActor, defaultRequestTimeout)
    val namespaceService = new NamespaceService(ec, namespaceActor, defaultRequestTimeout)
    val roleService = new RoleService(ec, roleActor, defaultRequestTimeout)
    val userApiKeyService = new CurrentUserApiKeyService(ec, userApiKeyActor, defaultRequestTimeout)
    val configService = new ConfigService(ec, configActor, defaultRequestTimeout)
    val statusService = new ServerStatusService(ec, statusActor, defaultRequestTimeout)
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

    // The authenticator that will be used to authenticate HTTP requests.
    val authenticator = new Authenticator(authStoreActor, defaultRequestTimeout, ec)

    implicit def rejectionHandler: RejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(message, _) =>
          complete(ErrorResponse("malformed_request_content", Some(message)))
        case AuthorizationFailedRejection =>
          complete(ForbiddenError)
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(methodNotAllowed(names))
      }
      .handleNotFound {
        complete(notFoundResponse(Some("The requested resource could not be found")))
      }
      .result()

    val corsSettings: CorsSettings = CorsSettings.defaultSettings.withAllowedMethods(
      List(
        HttpMethods.GET,
        HttpMethods.POST,
        HttpMethods.PUT,
        HttpMethods.DELETE,
        HttpMethods.HEAD,
        HttpMethods.OPTIONS))

    val route = cors(corsSettings) {
      handleExceptions(exceptionHandler) {
        // Authentication services can be called without being authenticated
        authService.route ~
          // Everything else must be authenticated as a convergence user.
          extractRequest { request =>
            authenticator.requireAuthenticatedUser(request) { authProfile =>
              concat(
                currentUserService.route(authProfile),
                statusService.route(authProfile),
                convergenceUserService.route(authProfile),
                namespaceService.route(authProfile),
                domainService.route(authProfile),
                roleService.route(authProfile),
                configService.route(authProfile),
                userApiKeyService.route(authProfile),
                keyGenService.route(),
                convergenceImportService.route(authProfile),
                databaseManagerService.route(authProfile))
            }
          }
      }
    }

    // Now we start up the server
    Http().bindAndHandle(route, interface, port).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        logger.info(s"Rest API started at: http://$interface:$port")
      case Failure(e) ⇒
        logger.info(s"Rest API binding failed with ${e.getMessage}")
        system.terminate()
    }
  }

  /**
   * Shuts down the REST API. This will stop any actors that were started
   * in the Actor System and stop the HTTP server.
   */
  def stop(): Unit = {
    logger.info("Convergence Rest API shutting down...")

    // Kill all the routers we started
    this.routers.foreach(router => router ! PoisonPill)

    // Unbind the HTTP Server
    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Rest API shut down")
    }
  }
}
