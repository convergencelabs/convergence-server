package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.json4s.jackson.Serialization

import com.convergencelabs.server.db.data.JsonFormats
import com.convergencelabs.server.db.schema.DatabaseManager.DatabaseVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetConvergenceVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.GetDomainVersion
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeConvergence
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeDomain
import com.convergencelabs.server.db.schema.DatabaseManagerActor.UpgradeDomains
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.rest.DatabaseManagerRestService.UpgradeRequest
import com.convergencelabs.server.frontend.rest.DatabaseManagerRestService.VersionResponse

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.handleWith
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import grizzled.slf4j.Logging

object DatabaseManagerRestService {
  case class UpgradeRequest(version: Option[Int], preRelease: Option[Boolean])
  case class VersionResponse(managerVersion: Int, databaseVersion: Int) extends AbstractSuccessResponse
}

class DatabaseManagerRestService(
  private[this] val executionContext: ExecutionContext,
  private[this] val databaseManager: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends Json4sSupport
    with Logging {

  implicit val serialization = Serialization
  implicit val formats = JsonFormats.format

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { adminUser: String =>
    (post & pathPrefix("upgrade")) {
      path("convergence") {
        handleWith(upgradeConvergence)
      } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
        entity(as[UpgradeRequest]) { request =>
          complete(upgradeDomain(namespace, domainId, request))
        }
      }
    } ~ (get & pathPrefix("version")) {
      path("convergence") {
        complete(getConvergenceVersion())
      } ~ path("domain" / Segment / Segment) { (namespace, domainId) =>
        complete(getDomainVersion(namespace, domainId))
      }
    }
  }

  def upgradeConvergence(request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = version.map(_.toString) getOrElse ("latest")
    logger.debug(s"Received an request to upgrade convergence database to version: ${to}")
    (databaseManager ? UpgradeConvergence(version, preRelease.getOrElse(false))).mapTo[Unit].map {
      case _ => OkResponse
    }
  }

  def upgradeDomain(namespace: String, domainId: String, request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = version.map(_.toString) getOrElse ("latest")
    logger.debug(s"Received an request to upgrade domain database to version: ${to}")
    (databaseManager ? UpgradeDomain(DomainFqn(namespace, domainId), version, preRelease.getOrElse(false))).mapTo[Unit].map {
      case _ => OkResponse
    }
  }
  
  def upgradeDomains(request: UpgradeRequest): Future[RestResponse] = {
    val UpgradeRequest(version, preRelease) = request
    val to = version.map(_.toString) getOrElse ("latest")
    logger.debug(s"Received an request to upgrade all domain databases to version: ${to}")
    (databaseManager ? UpgradeDomains(version, preRelease.getOrElse(false))).mapTo[Unit].map {
      case _ => OkResponse
    }
  }

  def getConvergenceVersion(): Future[RestResponse] = {
    (databaseManager ? GetConvergenceVersion).mapTo[DatabaseVersion].map {
      case DatabaseVersion(manager, db) => (StatusCodes.OK, VersionResponse(manager, db))
    }
  }

  def getDomainVersion(namespace: String, domainId: String): Future[RestResponse] = {
    (databaseManager ? GetDomainVersion(DomainFqn(namespace, domainId))).mapTo[DatabaseVersion].map {
      case DatabaseVersion(manager, db) => (StatusCodes.OK, VersionResponse(manager, db))
    }
  }
}
