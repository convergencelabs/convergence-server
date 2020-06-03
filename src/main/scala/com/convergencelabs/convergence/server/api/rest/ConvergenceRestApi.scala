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

import akka.actor
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, complete, concat, extractRequest, extractUri, handleExceptions}
import akka.http.scaladsl.server._
import akka.stream.Materializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.convergencelabs.convergence.server.api.rest.domain.DomainService
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException, InvalidValueException}
import com.convergencelabs.convergence.server.db.data.ConvergenceImporterActor
import com.convergencelabs.convergence.server.db.schema.DatabaseManagerActor
import com.convergencelabs.convergence.server.domain.chat.ChatActor
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
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
 * @param interface The network interface to bind to.
 * @param port      The network port to bind to.
 */
class ConvergenceRestApi(private[this] val interface: String,
                         private[this] val port: Int,
                         private[this] val context: ActorContext[_],
                         domainRestRegion: ActorRef[DomainRestActor.Message],
                         modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                         chatClusterRegion: ActorRef[ChatActor.Message])
  extends Logging with JsonSupport {

  private[this] implicit val system: ActorSystem[_] = context.system
  private[this] implicit val ec: ExecutionContextExecutor = system.executionContext
  private[this] implicit val defaultRequestTimeout: Timeout = Timeout(20 seconds)

  private[this] val routers = ListBuffer[ActorRef[_]]()
  private[this] var binding: Option[Http.ServerBinding] = None

  private[this] val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: DuplicateValueException =>
      complete(duplicateResponse(e.field))

    case e: InvalidValueException =>
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

    // Routers to backend services. We add them to a list so we can shut them down.
    val authenticationActor = createBackendRouter(context, AuthenticationActor.Key, "authActor")
    routers += authenticationActor
    val convergenceUserActor = createBackendRouter(context, ConvergenceUserManagerActor.Key, "convergenceUserActor")
    routers += convergenceUserActor
    val namespaceActor = createBackendRouter(context, NamespaceStoreActor.Key, "namespaceActor")
    routers += namespaceActor
    val databaseManagerActor = createBackendRouter(context, DatabaseManagerActor.Key, "databaseManagerActor")
    routers += databaseManagerActor
    val importerActor = createBackendRouter(context, ConvergenceImporterActor.Key, "importerActor")
    routers += importerActor
    val roleActor = createBackendRouter(context, RoleStoreActor.Key, "roleActor")
    routers += roleActor
    val userApiKeyActor = createBackendRouter(context, UserApiKeyStoreActor.Key, "userApiKeyActor")
    routers += userApiKeyActor
    val configActor = createBackendRouter(context, ConfigStoreActor.Key, "configActor")
    routers += configActor
    val favoriteDomainsActor = createBackendRouter(context, UserFavoriteDomainStoreActor.Key, "favoriteDomainsActor")
    routers += favoriteDomainsActor
    val domainStoreActor = createBackendRouter(context, DomainStoreActor.Key, "domainStoreActor")
    routers += domainStoreActor
    val statusActor = createBackendRouter(context, ServerStatusActor.Key, "serverStatusActor")
    routers += statusActor

    // The Rest Services
    val infoService = new InfoService(ec, defaultRequestTimeout)
    val authService = new AuthService(authenticationActor, system, ec, defaultRequestTimeout)
    val currentUserService = new CurrentUserService(convergenceUserActor, favoriteDomainsActor, ec, system, defaultRequestTimeout)
    val namespaceService = new NamespaceService(namespaceActor, system, ec, defaultRequestTimeout)
    val roleService = new UserRoleService(roleActor, system, ec , defaultRequestTimeout)
    val userApiKeyService = new CurrentUserApiKeyService(userApiKeyActor, system, ec, defaultRequestTimeout)
    val configService = new ConfigService(configActor, system, ec, defaultRequestTimeout)
    val statusService = new ServerStatusService(statusActor, system, ec, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)
    val convergenceUserService = new UserService(convergenceUserActor, system, ec, defaultRequestTimeout)

    // TODO re-enable these when permissions are handled properly.
//    val convergenceImportService = new ConvergenceImportService(ec, importerActor, defaultRequestTimeout)
//    val databaseManagerService = new DatabaseManagerRestService(ec, databaseManagerActor, defaultRequestTimeout)

    val domainService = new DomainService(
      system,
      ec,
      domainStoreActor,
      domainRestRegion,
      roleActor,
      modelClusterRegion,
      chatClusterRegion,
      defaultRequestTimeout)

    // The authenticator that will be used to authenticate HTTP requests.
    val authenticator = new Authenticator(authenticationActor, system, defaultRequestTimeout)

    val corsSettings: CorsSettings = CorsSettings.defaultSettings.withAllowedMethods(
      List(
        HttpMethods.GET,
        HttpMethods.POST,
        HttpMethods.PUT,
        HttpMethods.DELETE,
        HttpMethods.HEAD,
        HttpMethods.OPTIONS))

    implicit def rejectionHandler: RejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(message, _) =>
          cors(corsSettings) {
            complete(ErrorResponse("malformed_request_content", Some(message)))
          }
        case AuthorizationFailedRejection =>
          cors(corsSettings) {
            complete(ForbiddenError)
          }
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        cors(corsSettings) {
          complete(methodNotAllowed(names))
        }
      }
      .handleNotFound {
        cors(corsSettings) {
          complete(notFoundResponse(Some("The requested resource could not be found.")))
        }
      }
      .result()

    val route = cors(corsSettings) {
      handleExceptions(exceptionHandler) {
        infoService.route ~
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
                keyGenService.route())
//                convergenceImportService.route(authProfile),
//                databaseManagerService.route(authProfile))
            }
          }
      }
    }

    // Now we start up the server
    implicit val s: actor.ActorSystem = system.toClassic
    implicit val materializer: Materializer = akka.stream.Materializer.createMaterializer(s)
    Http().bindAndHandle(route, interface, port).onComplete {
      case Success(b) =>
        this.binding = Some(b)
        logger.info(s"Rest API started at: http://$interface:$port")
      case Failure(e) =>
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
    this.routers.foreach(context.stop)

    // Unbind the HTTP Server
    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Rest API shut down")
    }
  }
}
