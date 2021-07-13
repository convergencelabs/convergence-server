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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _string2NR, as, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.api.rest.domain.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.convergence.server.backend.services.domain.collection.CollectionStoreActor._
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.model.domain.collection.{Collection, CollectionAndUserPermissions, CollectionPermissions, CollectionSummary, CollectionWorldAndUserPermissions}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

private[domain] final class DomainCollectionService(domainRestActor: ActorRef[DomainRestActor.Message],
                                                    scheduler: Scheduler,
                                                    executionContext: ExecutionContext,
                                                    timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainCollectionService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Long].?, "limit".as[Long].?) { (filter, offset, limit) =>
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
        } ~ pathPrefix("permissions") {
          pathEnd {
            get {
              complete(getCollectionPermissions(domain, collectionId))
            }
          } ~ pathPrefix("world") {
            pathEnd {
              get {
                complete(getCollectionWorldPermissions(domain, collectionId))
              } ~ put {
                entity(as[CollectionPermissionsData]) { permissions =>
                  complete(setCollectionWorldPermissions(domain, collectionId, permissions))
                }
              }
            }
          } ~ pathPrefix("user") {
            pathEnd {
              get {
                complete(getCollectionUserPermissions(domain, collectionId))
              }
            } ~ pathPrefix(Segment) { username: String =>
              pathEnd {
                get {
                  complete(getCollectionPermissionsForUser(domain, collectionId, username))
                } ~ put {
                  entity(as[CollectionPermissionsData]) { permissions =>
                    complete(setCollectionPermissionsForUser(domain, collectionId, username, permissions))
                  }
                } ~ delete {
                  complete(removeCollectionPermissionsForUser(domain, collectionId, username))
                }
              }
            }
          }
        }
      }
    }
  } ~ pathPrefix("collectionSummary") {
    pathEnd {
      get {
        parameters("filter".?, "offset".as[Long].?, "limit".as[Long].?) { (filter, offset, limit) =>
          complete(getCollectionSummaries(domain, filter, offset, limit))
        }
      }
    }
  }

  private[this] def getCollections(domain: DomainId, filter: Option[String], offset: Option[Long], limit: Option[Long]): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionsResponse](
        r => DomainRestMessage(domain, GetCollectionsRequest(filter, QueryOffset(offset), QueryLimit(limit), r)))
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
          val collectionData = collectionToCollectionData(collection)
          okResponse(collectionData)
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
    val CollectionUpdateData(description, worldPermissions, userPermissions, overrideSnapshotConfig, snapshotConfig) = collectionUpdateData
    val collectionData = CollectionData(collectionId, description, worldPermissions, userPermissions, overrideSnapshotConfig, snapshotConfig)
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

  private[this] def getCollectionSummaries(domain: DomainId, filter: Option[String], offset: Option[Long], limit: Option[Long]): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionSummariesResponse](r =>
        DomainRestMessage(domain, GetCollectionSummariesRequest(filter, QueryOffset(offset), QueryLimit(limit), r)))
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

  //
  // Permissions
  //

  def getCollectionPermissions(domain: DomainId, collectionId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionPermissionsResponse](r =>
        DomainRestMessage(domain, GetCollectionPermissionsRequest(collectionId, r)))
      .map(_.permissions.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        { permissions =>
          val data = collectionWorldAndUserPermissionsToData(permissions)
          okResponse(data)
        }))
  }

  def getCollectionWorldPermissions(domain: DomainId, collectionId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionWorldPermissionsResponse](r =>
        DomainRestMessage(domain, GetCollectionWorldPermissionsRequest(collectionId, r)))
      .map(_.permissions.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        { permissions =>
          val data = collectionPermissionsToData(permissions)
          okResponse(data)
        }
      ))
  }

  def setCollectionWorldPermissions(domain: DomainId, collectionId: String, permissions: CollectionPermissionsData): Future[RestResponse] = {
    val p = dataToCollectionPermissions(permissions)
    domainRestActor
      .ask[SetCollectionWorldPermissionsResponse](r =>
        DomainRestMessage(domain, SetCollectionWorldPermissionsRequest(collectionId, p, r)))
      .map(_.response.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        {
          _ => OkResponse
        }
      ))
  }

  def getCollectionUserPermissions(domain: DomainId, collectionId: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionUserPermissionsResponse](r =>
        DomainRestMessage(domain, GetCollectionUserPermissionsRequest(collectionId, r)))
      .map(_.permissions.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        { permissions =>
          val data = permissions.map { case (k, v) => (k.username, collectionPermissionsToData(v)) }
          okResponse(data)
        }))
  }

  def getCollectionPermissionsForUser(domain: DomainId, collectionId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[GetCollectionPermissionsForUserResponse](r =>
        DomainRestMessage(domain, GetCollectionPermissionsForUserRequest(collectionId, DomainUserId.normal(username), r)))
      .map(_.permissions.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        { permissions =>
          val data = collectionPermissionsToData(permissions)
          okResponse(data)
        }
      ))
  }

  def setCollectionPermissionsForUser(domain: DomainId, collectionId: String, username: String, permissions: CollectionPermissionsData): Future[RestResponse] = {
    val p = dataToCollectionPermissions(permissions)
    domainRestActor
      .ask[SetCollectionPermissionsForUserResponse](r =>
        DomainRestMessage(domain, SetCollectionPermissionsForUserRequest(collectionId, DomainUserId.normal(username), p, r)))
      .map(_.response.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        {
          _ => OkResponse
        }
      ))
  }

  def removeCollectionPermissionsForUser(domain: DomainId, collectionId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[RemoveCollectionPermissionsForUserResponse](r =>
        DomainRestMessage(domain, RemoveCollectionPermissionsForUserRequest(collectionId, DomainUserId.normal(username), r)))
      .map(_.response.fold(
        {
          case UnknownError() => InternalServerError
          case CollectionNotFoundError() => NotFoundResponse
        },
        {
          _ => DeletedResponse
        }
      ))
  }


  //
  // Mapping
  //

  private[this] def collectionDataToCollection(collectionData: CollectionData): CollectionAndUserPermissions = {
    val CollectionData(
    id,
    description,
    CollectionPermissionsData(create, read, write, remove, manage),
    userPermissionsData,
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
    val snapshotConfig = model.domain.ModelSnapshotConfig(
      snapshotsEnabled,
      triggerByVersion,
      limitByVersion,
      minimumVersionInterval,
      maximumVersionInterval,
      triggerByTime,
      limitByTime,
      Duration.ofMillis(minimumTimeInterval),
      Duration.ofMillis(maximumTimeInterval))

    val userPermissions = userPermissionsData.map { case (username, permissions) =>
      username -> dataToCollectionPermissions(permissions)
    }

    val collection = Collection(
      id, description, overrideSnapshotConfig, snapshotConfig,
      CollectionPermissions(create, read, write, remove, manage),
      )
    CollectionAndUserPermissions(collection, userPermissions)
  }

  private[this] def collectionToCollectionData(cap: CollectionAndUserPermissions): CollectionData = {
    val CollectionAndUserPermissions(collection, userPermissions) = cap
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
    val worldPermissionsData = CollectionPermissionsData(create, read, write, remove, manage)
    val userPermissionsData = userPermissions.map { case (userId, permissions) =>
      userId -> collectionPermissionsToData(permissions)
    }
    val collectionData = CollectionData(
      id, description, worldPermissionsData, userPermissionsData, overrideSnapshotConfig, snapshotConfig)
    collectionData
  }

  private[this] def collectionPermissionsToData(permissions: CollectionPermissions): CollectionPermissionsData = {
    val CollectionPermissions(create, read, write, remove, manage) = permissions
    CollectionPermissionsData(create, read, write, remove, manage)
  }

  private[this] def dataToCollectionPermissions(permissions: CollectionPermissionsData): CollectionPermissions = {
    val CollectionPermissionsData(create, read, write, remove, manage) = permissions
    CollectionPermissions(create, read, write, remove, manage)
  }

  private[this] def collectionWorldAndUserPermissionsToData(permissions: CollectionWorldAndUserPermissions): CollectionWorldAndUserPermissionsData = {
    val CollectionWorldAndUserPermissions(world, user) = permissions
    CollectionWorldAndUserPermissionsData(
      collectionPermissionsToData(world),
      user.map { case (k, v) => (k, collectionPermissionsToData(v)) })
  }
}

object DomainCollectionService {

  case class CollectionData(id: String,
                            description: String,
                            worldPermissions: CollectionPermissionsData,
                            userPermissions: Map[DomainUserId, CollectionPermissionsData],
                            overrideSnapshotPolicy: Boolean,
                            snapshotPolicy: ModelSnapshotPolicyData)

  case class CollectionUpdateData(description: String,
                                  worldPermissions: CollectionPermissionsData,
                                  userPermissions: Map[DomainUserId, CollectionPermissionsData],
                                  overrideSnapshotPolicy: Boolean,
                                  snapshotPolicy: ModelSnapshotPolicyData)

  case class CollectionSummaryData(id: String,
                                   description: String,
                                   modelCount: Long)

  case class CollectionPermissionsData(create: Boolean,
                                       read: Boolean,
                                       write: Boolean,
                                       remove: Boolean,
                                       manage: Boolean)

  case class CollectionWorldAndUserPermissionsData(worldPermissions: CollectionPermissionsData,
                                                   userPermissions: Map[DomainUserId, CollectionPermissionsData])
}
