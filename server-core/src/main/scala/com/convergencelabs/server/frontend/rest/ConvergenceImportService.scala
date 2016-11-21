package com.convergencelabs.server.frontend.rest

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.json4s.jackson.Serialization

import com.convergencelabs.server.db.data.ConvergenceImporterActor.ConvergenceImport
import com.convergencelabs.server.db.data.ConvergenceImporterActor.DomainExport
import com.convergencelabs.server.db.data.ConvergenceScript
import com.convergencelabs.server.db.data.JsonFormats
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging

object ConvergenceImportService {
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
    pathPrefix("import") {
      pathPrefix("convergence") {
        pathEnd {
          post {
            handleWith(importConvergence)
          }
        }
      }
    } ~ pathPrefix("export") {
      pathPrefix("domain") {
        pathPrefix(Segment / Segment) { (namespace, domainId) =>
          pathEnd {
            complete(exportDomain(namespace, domainId))
          }
        }
      }
    }
  }

  def importConvergence(script: ConvergenceScript): Future[RestResponse] = {
    logger.debug(s"Received an import request: ${script}")
    (importerActor ? ConvergenceImport(script)).mapTo[Unit].map {
      case _ => OkResponse
    }
  }

  def exportDomain(namespace: String, domainId: String): Future[RestResponse] = {
    logger.debug(s"Received a domain export request: ${namespace}/${domainId}")
    (importerActor ? DomainExport(DomainFqn(namespace, domainId))).mapTo[Unit].map {
      case _ => OkResponse
    }
  }
}
