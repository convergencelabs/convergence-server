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

package com.convergencelabs.convergence.server.api.rest.domain

import java.time.Duration

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _string2NR, as, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.api.rest.domain.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.convergence.server.datastore.domain.CollectionPermissions
import com.convergencelabs.convergence.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.convergence.server.datastore.domain.CollectionStoreActor._
import com.convergencelabs.convergence.server.domain.model.Collection
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.domain.{DomainId, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

class DomainCollectionService(domainRestActor: ActorRef[DomainRestActor.Message],
                              scheduler: Scheduler,
                              executionContext: ExecutionContext,
                              timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainCollectionService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Int].?, "limit".as[Int].?) { (filter, offset, limit) =>
            complete(getCollections(domain, filter, offset, limit))
          }
        } ~ post {
          entity(as[CollectionData]) { collection =>
            complete(createCollection(domain, collection))
          }
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            complete(getCollection(domain, collectionId))
          } ~ delete {
            complete(deleteCollection(domain, collectionId))
          } ~ put {
            entity(as[CollectionUpdateData]) { updateData =>
              complete(updateCollection(domain, collectionId, updateData))
            }
          }
        }
      }
    }
  } ~ pathPrefix("collectionSummary") {
    pathEnd {
      get {
        parameters("filter".?, "offset".as[Int].?, "limit".as[Int].?) { (filter, offset, limit) =>
          complete(getCollectionSummaries(domain, filter, offset, limit))
        }
      }
    }
  }

  private[this] def getCollections(domain: DomainId, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionsResponse](
        r => DomainRestMessage(domain, GetCollectionsRequest(filter, offset, limit, r)))
      .map(_.collections.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { results =>
          val collections = results.data.map(collectionToCollectionData)
          okResponse(PagedRestResponse(collections, results.offset, results.count))

        })
      )
  }

  private[this] def getCollection(domain: DomainId, collectionId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionResponse](
        r => DomainRestMessage(domain, GetCollectionRequest(collectionId, r)))
      .map(_.collection.fold(
        {
          case CollectionNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { collection =>
          okResponse(collection)
        })
      )
  }

  private[this] def createCollection(domain: DomainId, collectionData: CollectionData): Future[RestResponse] = {
    val collection = this.collectionDataToCollection(collectionData)
    domainRestActor
      .ask[CreateCollectionResponse](
        r => DomainRestMessage(domain, CreateCollectionRequest(collection, r)))
      .map(_.response.fold(
        {
          case CollectionExists(field) =>
            duplicateResponse(s"A collection with the specified '$field' already exists")
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          CreatedResponse
        })
      )

  }

  private[this] def updateCollection(domain: DomainId, collectionId: String, collectionUpdateData: CollectionUpdateData): Future[RestResponse] = {
    val CollectionUpdateData(description, worldPermissions, overrideSnapshotConfig, snapshotConfig) = collectionUpdateData
    val collectionData = CollectionData(collectionId, description, worldPermissions, overrideSnapshotConfig, snapshotConfig)
    val collection = this.collectionDataToCollection(collectionData)
    domainRestActor
      .ask[UpdateCollectionResponse](r =>
        DomainRestMessage(domain, UpdateCollectionRequest(collectionId, collection, r)))
      .map(_.response.fold(
        {
          case CollectionNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        })
      )
  }

  private[this] def deleteCollection(domain: DomainId, collectionId: String): Future[RestResponse] = {
    domainRestActor
      .ask[DeleteCollectionResponse](r =>
        DomainRestMessage(domain, DeleteCollectionRequest(collectionId, r)))
      .map(_.response.fold(
        {
          case CollectionNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          DeletedResponse
        })
      )
  }

  private[this] def getCollectionSummaries(domain: DomainId, filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionSummariesResponse](r =>
        DomainRestMessage(domain, GetCollectionSummariesRequest(filter, offset, limit, r)))
      .map(_.collections.fold(
        {
          case UnknownError() =>
            InternalServerError
        },
        { results =>
          val collections = results.data.map { c =>
            val CollectionSummary(id, desc, count) = c
            CollectionSummaryData(id, desc, count)
          }
          val response = PagedRestResponse(collections, results.offset, results.count)
          okResponse(response)
        }))
  }

  private[this] def collectionDataToCollection(collectionData: CollectionData): Collection = {
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

  private[this] def collectionToCollectionData(collection: Collection): CollectionData = {
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

object DomainCollectionService {

  case class CollectionPermissionsData(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean, create: Boolean)

  case class CollectionData(id: String,
                            description: String,
                            worldPermissions: CollectionPermissionsData,
                            overrideSnapshotPolicy: Boolean,
                            snapshotPolicy: ModelSnapshotPolicyData)

  case class CollectionUpdateData(description: String,
                                  worldPermissions: CollectionPermissionsData,
                                  overrideSnapshotPolicy: Boolean,
                                  snapshotPolicy: ModelSnapshotPolicyData)

  case class CollectionSummaryData(id: String,
                                   description: String,
                                   modelCount: Int)

}