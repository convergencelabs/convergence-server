package com.convergencelabs.server.db.data

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.json4s.jackson.Serialization

import com.convergencelabs.server.db.data.ConvergenceImportService.DomainExportRestResponse
import com.convergencelabs.server.db.data.ConvergenceImporterActor.ConvergenceImport
import com.convergencelabs.server.db.data.ConvergenceImporterActor.DomainExport
import com.convergencelabs.server.db.data.ConvergenceImporterActor.DomainExportResponse
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.rest.AbstractSuccessResponse
import com.convergencelabs.server.frontend.rest.OkResponse
import com.convergencelabs.server.frontend.rest.RestResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.get
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging
import com.convergencelabs.server.db.data.ConvergenceImporterActor.ConvergenceExport
import com.convergencelabs.server.db.data.ConvergenceImporterActor.ConvergenceExportResponse
import com.convergencelabs.server.db.data.ConvergenceImportService.ConvergenceExportRestResponse
import com.convergencelabs.server.frontend.realtime.MappedTypeHits

object ConvergenceImportService {
  case class DomainExportRestResponse(export: DomainScript) extends AbstractSuccessResponse
  case class ConvergenceExportRestResponse(export: ConvergenceScript) extends AbstractSuccessResponse
}

class ConvergenceImportService(
  private[this] val executionContext: ExecutionContext,
  private[this] val importerActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends Json4sSupport
    with Logging {

  implicit val serialization = Serialization
  implicit val formats = JsonFormats.format

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { adminUser: String =>
    pathPrefix("data") {
      (post & pathPrefix("import")) {
        path("convergence") {
          handleWith(importConvergence)
        }
      } ~ (get & pathPrefix("export")) {
        path("domain" / Segment / Segment) { (namespace, domainId) =>
          complete(exportDomain(namespace, domainId))
        } ~ path("convergence" / Segment) { (username) =>
          complete(exportUser(username))
        }
      }
    }
  }

  def importConvergence(script: ConvergenceScript): Future[RestResponse] = {
    logger.debug(s"Received a convergence import request")
    (importerActor ? ConvergenceImport(script)).mapTo[Unit].map {
      case _ => OkResponse
    }
  }

  def exportDomain(namespace: String, domainId: String): Future[RestResponse] = {
    logger.debug(s"Received a domain export request: ${namespace}/${domainId}")
    (importerActor ? DomainExport(DomainFqn(namespace, domainId))).mapTo[DomainExportResponse].map {
      case DomainExportResponse(script) => (StatusCodes.OK, DomainExportRestResponse(script))
    }
  }

  def exportUser(username: String): Future[RestResponse] = {
    logger.debug(s"Received a convergence export request for user: ${username}")
    (importerActor ? ConvergenceExport(Some(username))).mapTo[ConvergenceExportResponse].map {
      case ConvergenceExportResponse(script) => (StatusCodes.OK, ConvergenceExportRestResponse(script))
    }
  }
}
