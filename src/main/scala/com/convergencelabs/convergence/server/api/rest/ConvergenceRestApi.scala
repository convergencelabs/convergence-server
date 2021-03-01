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

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ActorContext, Routers}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, complete, concat, extractRequest, extractUri, handleExceptions}
import akka.http.scaladsl.server._
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.convergencelabs.convergence.server.api.rest.domain.DomainService
import com.convergencelabs.convergence.server.backend.services.server.DatabaseManagerActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor
import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.server._
import grizzled.slf4j.Logging

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

/**
 * The [[ConvergenceRestApi]] class is the main entry point to the Convergence
 * Server REST Subsystem.
 *
 * @param interface          The network interface to bind to.
 * @param port               The network port to bind to.
 * @param context            The ActorContext to use to spawn actors.
 * @param domainRestRegion   The shard region that hosts the
 *                           DomainRestActors.
 * @param modelClusterRegion The shard region that hosts the
 *                           RealtimeModelActors
 * @param chatClusterRegion  The shard region that hosts the
 *                           ChatActors.
 */
private[server] final class ConvergenceRestApi(interface: String,
                                               port: Int,
                                               context: ActorContext[_],
                                               domainRestRegion: ActorRef[DomainRestActor.Message],
                                               modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                                               chatClusterRegion: ActorRef[ChatActor.Message])
  extends Logging with JsonSupport {

  private[this] implicit val system: ActorSystem[_] = context.system
  private[this] implicit val ec: ExecutionContextExecutor = system.executionContext
  private[this] implicit val defaultRequestTimeout: Timeout = Timeout(20 seconds)

  private[this] val routers = ListBuffer[ActorRef[_]]()
  private[this] var binding: Option[Http.ServerBinding] = None
  private[this] implicit val materializer: Materializer = SystemMaterializer.get(system).materializer

  private[this] val exceptionHandler: ExceptionHandler = ExceptionHandler {
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
  def start(): Future[Unit] = {

    // Routers to backend services. We add them to a list so we can shut them down.
    val authenticationActor = createBackendRouter(context, AuthenticationActor.Key, "authActor")
    routers += authenticationActor
    val convergenceUserActor = createBackendRouter(context, UserStoreActor.Key, "convergenceUserActor")
    routers += convergenceUserActor
    val namespaceActor = createBackendRouter(context, NamespaceStoreActor.Key, "namespaceActor")
    routers += namespaceActor
    val databaseManagerActor = createBackendRouter(context, DatabaseManagerActor.Key, "databaseManagerActor")
    routers += databaseManagerActor
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
    val authService = new AuthService(authenticationActor, system.scheduler, ec, defaultRequestTimeout)
    val currentUserService = new CurrentUserService(convergenceUserActor, favoriteDomainsActor, ec, system.scheduler, defaultRequestTimeout)
    val namespaceService = new NamespaceService(namespaceActor, system.scheduler, ec, defaultRequestTimeout)
    val roleService = new UserRoleService(roleActor, system.scheduler, ec, defaultRequestTimeout)
    val userApiKeyService = new CurrentUserApiKeyService(userApiKeyActor, system.scheduler, ec, defaultRequestTimeout)
    val configService = new ConfigService(configActor, system.scheduler, ec, defaultRequestTimeout)
    val statusService = new ServerStatusService(statusActor, system.scheduler, ec, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)
    val convergenceUserService = new UserService(convergenceUserActor, system.scheduler, ec, defaultRequestTimeout)
    val databaseManagerService = new DatabaseManagerRestService(ec, system.scheduler, databaseManagerActor, defaultRequestTimeout)

    val domainService = new DomainService(
      system.scheduler,
      ec,
      domainStoreActor,
      domainRestRegion,
      roleActor,
      modelClusterRegion,
      chatClusterRegion,
      defaultRequestTimeout)

    // The authenticator that will be used to authenticate HTTP requests.
    val authenticator = new Authenticator(
      authenticationActor, system.scheduler, system.executionContext, defaultRequestTimeout)

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
            complete(badRequest(message))
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
                keyGenService.route(),
                databaseManagerService.route(authProfile))
            }
          }
      }
    }

    // Now we start up the server
    Http()(system.classicSystem)
      .newServerAt(interface, port)
      .bind(route)
      .map { binding =>
        this.binding = Some(binding)
        logger.info(s"Rest API started at: http://$interface:$port")
      }
      .recoverWith { cause =>
        logger.error(s"Rest API startup failed", cause)
        Future.failed(cause)
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

  private[this] def createBackendRouter[T](context: ActorContext[_], serviceKey: ServiceKey[T], localName: String): ActorRef[T] = {
    val group = Routers.group(serviceKey).withRoundRobinRouting()
    context.spawn(group, localName)
  }
}
