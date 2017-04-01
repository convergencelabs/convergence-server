package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.UpdateCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.DeleteCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollections
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.model.Collection

import DomainCollectionService.GetCollectionResponse
import DomainCollectionService.GetCollectionsResponse
import DomainCollectionService.CollectionData
import DomainCollectionService.CollectionPermissionsData

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import scala.util.Try
import com.convergencelabs.server.frontend.rest.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.server.domain.ModelSnapshotConfig
import java.time.Duration
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollectionSummaries
import com.convergencelabs.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.server.frontend.rest.DomainCollectionService.GetCollectionSummaryResponse
import com.convergencelabs.server.frontend.rest.DomainCollectionService.CollectionSummaryData

object DomainCollectionService {
  case class GetCollectionsResponse(collections: List[CollectionData]) extends AbstractSuccessResponse
  case class GetCollectionResponse(collection: CollectionData) extends AbstractSuccessResponse
  case class CollectionPermissionsData(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean, create: Boolean)
  case class CollectionData(
    id: String,
    description: String,
    worldPermissions: CollectionPermissionsData,
    overrideSnapshotConfig: Boolean,
    snapshotConfig: ModelSnapshotPolicyData)

  case class GetCollectionSummaryResponse(collections: List[CollectionSummaryData]) extends AbstractSuccessResponse
  case class CollectionSummaryData(
    id: String,
    description: String,
    modelCount: Int)
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getCollections(domain))
          }
        } ~ post {
          entity(as[CollectionData]) { collection =>
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(createCollection(domain, collection))
            }
          }
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(getCollection(domain, collectionId))
            }
          } ~ delete {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(deleteCollection(domain, collectionId))
            }
          } ~ put {
            entity(as[CollectionData]) { updateData =>
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(updateCollection(domain, collectionId, updateData))
              }
            }
          }
        }
      }
    }
  } ~ pathPrefix("collectionSummary") {
    pathEnd {
      get {
        parameters("limit".as[Int].?, "offset".as[Int].?) { (limit, offset) =>
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getCollectionSummaries(domain, limit, offset))
          }
        }
      }
    }
  }

  def getCollections(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetCollections(None, None))
    (domainRestActor ? message).mapTo[List[Collection]] map (collections =>
      (StatusCodes.OK, GetCollectionsResponse(collections.map(collectionToCollectionData(_)))))
  }

  def getCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetCollection(collectionId))
    (domainRestActor ? message).mapTo[Option[Collection]] map {
      case Some(collection) => (StatusCodes.OK, GetCollectionResponse(collectionToCollectionData(collection)))
      case None => NotFoundError
    }
  }

  def createCollection(domain: DomainFqn, collectionData: CollectionData): Future[RestResponse] = {
    val collection = this.collectionDataToCollection(collectionData)
    val message = DomainMessage(domain, CreateCollection(collection))
    (domainRestActor ? message) map { _ => CreateRestResponse }
  }

  def updateCollection(domain: DomainFqn, collectionId: String, collectionData: CollectionData): Future[RestResponse] = {
    val collection = this.collectionDataToCollection(collectionData)
    val message = DomainMessage(domain, UpdateCollection(collectionId, collection))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, DeleteCollection(collectionId))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getCollectionSummaries(domain: DomainFqn, limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    val message = DomainMessage(domain, GetCollectionSummaries(limit, offset))
    (domainRestActor ? message).mapTo[List[CollectionSummary]] map (collections =>
      (StatusCodes.OK, GetCollectionSummaryResponse(collections.map { c =>
        val CollectionSummary(id, desc, count) = c
        CollectionSummaryData(id, desc, count)
      })))
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }

  def collectionDataToCollection(collectionData: CollectionData): Collection = {
    val CollectionData(
      id,
      description,
      CollectionPermissionsData(read, write, remove, manage, create),
      overrideSnapshotConfig,
      ModelSnapshotPolicyData(
        snapshotsEnabled,
        triggerByVersion,
        maximumVersionInterval,
        limitByVersion,
        minimumVersionInterval,
        triggerByTime,
        maximumTimeInterval,
        limitByTime,
        minimumTimeInterval
        )
      ) = collectionData
    val snapshotConfig = ModelSnapshotConfig(
      snapshotsEnabled,
      triggerByVersion,
      limitByVersion,
      minimumVersionInterval,
      maximumVersionInterval,
      triggerByTime,
      limitByTime,
      Duration.ofMillis(minimumTimeInterval),
      Duration.ofMillis(maximumTimeInterval))
    val collection = Collection(id, description, overrideSnapshotConfig, snapshotConfig, CollectionPermissions(create, read, write, remove, manage))
    collection
  }

  def collectionToCollectionData(collection: Collection): CollectionData = {
    val Collection(
      id,
      description,
      overrideSnapshotConfig,
      ModelSnapshotConfig(
        snapshotsEnabled,
        triggerByVersion,
        limitByVersion,
        minimumVersionInterval,
        maximumVersionInterval,
        triggerByTime,
        limitByTime,
        minimumTimeInterval,
        maximumTimeInterval
        ),
      CollectionPermissions(create, read, write, remove, manage)
      ) = collection
    val snapshotConfig = ModelSnapshotPolicyData(
      snapshotsEnabled,
      triggerByVersion,
      maximumVersionInterval,
      limitByVersion,
      minimumVersionInterval,
      triggerByTime,
      maximumTimeInterval.toMillis,
      limitByTime,
      minimumTimeInterval.toMillis)
    val worldPermissions = CollectionPermissionsData(read, write, remove, manage, create)
    val collectionData = CollectionData(id, description, worldPermissions, overrideSnapshotConfig, snapshotConfig)
    collectionData
  }
}
