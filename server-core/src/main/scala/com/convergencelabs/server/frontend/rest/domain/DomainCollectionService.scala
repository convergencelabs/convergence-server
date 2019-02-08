package com.convergencelabs.server.frontend.rest

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.DeleteCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollectionSummaries
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollections
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.UpdateCollection
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.frontend.rest.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout


object DomainCollectionService {
  case class GetCollectionsResponse(collections: List[CollectionData])
  case class GetCollectionResponse(collection: CollectionData)
  case class CollectionPermissionsData(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean, create: Boolean)
  case class CollectionData(
    id: String,
    description: String,
    worldPermissions: CollectionPermissionsData,
    overrideSnapshotConfig: Boolean,
    snapshotConfig: ModelSnapshotPolicyData)

  case class GetCollectionSummaryResponse(collections: List[CollectionSummaryData])
  case class CollectionSummaryData(
    id: String,
    description: String,
    modelCount: Int)
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
    extends DomainRestService(executionContext, timeout) {

  import DomainCollectionService._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          authorize(canAccessDomain(domain, authProfile)) {
            complete(getCollections(domain))
          }
        } ~ post {
          entity(as[CollectionData]) { collection =>
            authorize(canAccessDomain(domain, authProfile)) {
              complete(createCollection(domain, collection))
            }
          }
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(getCollection(domain, collectionId))
            }
          } ~ delete {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(deleteCollection(domain, collectionId))
            }
          } ~ put {
            entity(as[CollectionData]) { updateData =>
              authorize(canAccessDomain(domain, authProfile)) {
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
          authorize(canAccessDomain(domain, authProfile)) {
            complete(getCollectionSummaries(domain, limit, offset))
          }
        }
      }
    }
  }

  def getCollections(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetCollections(None, None))
    (domainRestActor ? message).mapTo[List[Collection]] map (collections =>
      okResponse(GetCollectionsResponse(collections.map(collectionToCollectionData(_)))))
  }

  def getCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetCollection(collectionId))
    (domainRestActor ? message).mapTo[Option[Collection]] map {
      case Some(collection) => okResponse(GetCollectionResponse(collectionToCollectionData(collection)))
      case None => notFoundResponse()
    }
  }

  def createCollection(domain: DomainFqn, collectionData: CollectionData): Future[RestResponse] = {
    val collection = this.collectionDataToCollection(collectionData)
    val message = DomainRestMessage(domain, CreateCollection(collection))
    (domainRestActor ? message) map { _ => CreatedResponse }
  }

  def updateCollection(domain: DomainFqn, collectionId: String, collectionData: CollectionData): Future[RestResponse] = {
    val collection = this.collectionDataToCollection(collectionData)
    val message = DomainRestMessage(domain, UpdateCollection(collectionId, collection))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, DeleteCollection(collectionId))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getCollectionSummaries(domain: DomainFqn, limit: Option[Int], offset: Option[Int]): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetCollectionSummaries(limit, offset))
    (domainRestActor ? message).mapTo[List[CollectionSummary]] map (collections =>
      okResponse(GetCollectionSummaryResponse(collections.map { c =>
        val CollectionSummary(id, desc, count) = c
        CollectionSummaryData(id, desc, count)
      })))
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
