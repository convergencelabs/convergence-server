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
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsDirectives.cors
import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.RestAuthnorizationActor
import ch.megard.akka.http.cors.CorsSettings
import akka.http.scaladsl.model.headers.HttpOrigin
import akka.http.scaladsl.model.headers.HttpOriginRange
import ch.megard.akka.http.cors.HttpHeaderRange
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import akka.http.scaladsl.server.directives.Credentials
import com.convergencelabs.server.datastore.RegistrationActor
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.StatusCode

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
    extends Logging {

  implicit val s = system
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val defaultRequestTimeout = Timeout(20 seconds)

  def start(): Unit = {
    // FIXME this is a hack all of this should be a rest backend
    val dbConfig = system.settings.config.getConfig("convergence.convergence-database")
    val baseUri = dbConfig.getString("uri")
    val fullUri = baseUri + "/" + dbConfig.getString("database")
    val username = dbConfig.getString("username")
    val password = dbConfig.getString("password")

    val adminsConfig = system.settings.config.getConfig("convergence.convergence-admins")

    val dbPool = new OPartitionedDatabasePool(fullUri, password, password)
    val domainStore = new DomainStore(dbPool)

    val authActor = system.actorOf(AuthStoreActor.props(dbPool))
    val domainActor = system.actorOf(DomainStoreActor.props(dbPool))
    val userManagerActor = system.actorOf(ConvergenceUserManagerActor.props(dbPool, domainActor))
    val registrationActor = system.actorOf(RegistrationActor.props(dbPool, userManagerActor))
    val domainManagerActor = system.actorOf(RestDomainManagerActor.props(dbPool))
    val authzActor = system.actorOf(RestAuthnorizationActor.props(domainStore))
    val convergenceUserActor = system.actorOf(ConvergenceUserManagerActor.props(dbPool, domainActor))

    val dbPoolManager = system.actorOf(
      DomainPersistenceManagerActor.props(baseUri, domainStore),
      DomainPersistenceManagerActor.RelativePath)
    // Down to here

    // These are the rest services
    val authService = new AuthService(ec, authActor, defaultRequestTimeout)
    val authenticator = new Authenticator(authActor, defaultRequestTimeout, ec)
    val registrationService = new RegistrationService(ec, registrationActor, defaultRequestTimeout)
    val domainService = new DomainService(ec, authzActor, domainActor, domainManagerActor, defaultRequestTimeout)
    val keyGenService = new KeyGenService(ec)
    val convergenceAdminService = new ConvergenceAdminService(ec, convergenceUserActor, defaultRequestTimeout)

    def getApprovalHtml(token: String): String = {
      val htmlBuilder = StringBuilder.newBuilder
      htmlBuilder ++= "<!DOCTYPE html>\n"
      htmlBuilder ++= "<html lang='en'>\n"
      htmlBuilder ++= "<head>\n"
      htmlBuilder ++= "  <meta charset='UTF-8'>\n"
      htmlBuilder ++= "  <title>Title</title>\n"
      htmlBuilder ++= "</head>\n"
      htmlBuilder ++= "<body>\n"
      htmlBuilder ++= "  <button id='approveButton' onclick='approve();'>Approve</button>\n"
      htmlBuilder ++= "</body>\n"
      htmlBuilder ++= "<body>\n"
      htmlBuilder ++= "  <button id='rejectButton' onclick='post();'>Reject</button>\n"
      htmlBuilder ++= "</body>\n"
      htmlBuilder ++= "<script>\n"
      htmlBuilder ++= "  function approve() {\n"
      htmlBuilder ++= "    try {\n"
      htmlBuilder ++= "      var req = new XMLHttpRequest();\n"
      htmlBuilder ++= "      req.open('POST', 'http://localhost:8081/rest/registration/approve', true);\n"
      htmlBuilder ++= "      req.setRequestHeader('Content-type', 'application/json');\n"
      htmlBuilder ++= "      req.setRequestHeader('Access-Control-Allow-Origin', '*');\n"
      htmlBuilder ++= s"      req.send(JSON.stringify({'token': '${token}'}));\n"
      htmlBuilder ++= "    } catch (e) {\n"
      htmlBuilder ++= "      window.console && console.log(e);\n"
      htmlBuilder ++= "    }\n"
      htmlBuilder ++= "  }\n"
      htmlBuilder ++= "  function reject() {\n"
      htmlBuilder ++= "    try {\n"
      htmlBuilder ++= "      var req = new XMLHttpRequest();\n"
      htmlBuilder ++= "      req.open('POST', 'http://localhost:8081/rest/registration/reject', true);\n"
      htmlBuilder ++= "      req.setRequestHeader('Content-type', 'application/json');\n"
      htmlBuilder ++= "      req.setRequestHeader('Access-Control-Allow-Origin', '*');\n"
      htmlBuilder ++= s"      req.send(JSON.stringify({'token': '${token}'}));\n"
      htmlBuilder ++= "    } catch (e) {\n"
      htmlBuilder ++= "      window.console && console.log(e);\n"
      htmlBuilder ++= "    }\n"
      htmlBuilder ++= "  }\n"
      htmlBuilder ++= "</script>\n"
      htmlBuilder ++= "</html>\n"
      htmlBuilder.toString()
    }

    val route = cors(ConvergenceRestFrontEnd.ConvergenceCorsSettings) {
      // All request are under the "rest" path.
      pathPrefix("rest") {
        // You can call the auth service without being authenticated
        registrationService.route ~
          authService.route ~
          // Everything else must be authenticated
          extractRequest { request =>
            authenticator.requireAuthenticated(request) { userId =>
              domainService.route(userId) ~
                keyGenService.route()
            }
          }
      } ~ pathPrefix("admin") {
        authenticateBasic(realm = "convergence admin", AdminAuthenticator.authenticate(adminsConfig)) { adminUser =>
          convergenceAdminService.route(adminUser)
        }
      } ~ pathPrefix("approval") {
        pathPrefix(Segment) { token =>
          pathEnd {
            get {
              complete(StatusCodes.OK, HttpEntity(ContentType.WithCharset(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), getApprovalHtml(token)))
            }
          }
        }
      }
    }

    // Now we start up the server
    val bindingFuture = Http().bindAndHandle(route, interface, port)
    logger.info(s"Convergence Rest Front End listening at http://${interface}:${port}/")
  }
}
