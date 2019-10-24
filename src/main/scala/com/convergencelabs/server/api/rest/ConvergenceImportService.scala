package com.convergencelabs.server.api.rest

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.db.data.ConvergenceImporterActor._
import com.convergencelabs.server.db.data.{ConvergenceScript, JsonFormats}
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.security.AuthorizationProfile
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.{ExecutionContext, Future}

class ConvergenceImportService(
  private[this] val executionContext: ExecutionContext,
  private[this] val importerActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends Json4sSupport
    with Logging {

  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask
  
  implicit val serialization: Serialization.type = Serialization
  implicit val formats: Formats = JsonFormats.format

  implicit val ec: ExecutionContext = executionContext
  implicit val t: Timeout = defaultTimeout

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("data") {
      (post & pathPrefix("import")) {
        path("convergence") {
          handleWith(importConvergence)
        }
      } ~ (get & pathPrefix("export")) {
        path("domain" / Segment / Segment) { (namespace, domainId) =>
          complete(exportDomain(namespace, domainId))
        } ~ path("convergence" / Segment) { username =>
          complete(exportUser(username))
        }
      }
    }
  }

  def importConvergence(script: ConvergenceScript): Future[RestResponse] = {
    logger.debug(s"Received a convergence import request")
    (importerActor ? ConvergenceImport(script)).mapTo[Unit].map(_ => OkResponse)
  }

  def exportDomain(namespace: String, domainId: String): Future[RestResponse] = {
    logger.debug(s"Received a domain export request: $namespace/$domainId")
    (importerActor ? DomainExport(DomainId(namespace, domainId))).mapTo[DomainExportResponse].map {
      case DomainExportResponse(script) => okResponse(script)
    }
  }

  def exportUser(username: String): Future[RestResponse] = {
    logger.debug(s"Received a convergence export request for user: $username")
    (importerActor ? ConvergenceExport(Some(username))).mapTo[ConvergenceExportResponse].map {
      case ConvergenceExportResponse(script) => okResponse(script)
    }
  }
}
