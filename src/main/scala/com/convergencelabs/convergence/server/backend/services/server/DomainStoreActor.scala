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

package com.convergencelabs.convergence.server.backend.services.server

import akka.actor.typed.pubsub.Topic.Publish
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence._
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.backend.services.server.DomainDatabaseManagerActor.{DestroyDomainRequest, DestroyDomainResponse}
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic.DomainAvailabilityChanged
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.CollectionConfig
import com.convergencelabs.convergence.server.model.server.domain.{Domain, DomainAvailability, DomainDatabase, DomainStatus}
import com.convergencelabs.convergence.server.model.server.role.DomainRoleTarget
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import grizzled.slf4j.Logging

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * The [[DomainStoreActor]] handles request to manage the set of domains that
 * exist in the system.
 *
 * @param context              The actor context for this actor.
 * @param domainStore          The persistence store for domains.
 * @param configStore          The persistence store for system configuration.
 * @param roleStore            The persistence store for user roles.
 * @param favoriteDomainStore  The persistence store for setting favorite
 *                             domains.
 *                             for each user.
 * @param deltaLogStore        The persistence store for domain database
 *                             deltas.
 * @param versionLogStore      The persistence store for the domain database
 *                             version log.
 * @param domainCreator        A helper class that will create domains.
 * @param domainDbManagerActor A help class that orchestrates the creation
 *                             of databases for domains.
 * @param domainLifecycleTopic A pub/sub topic to broadcast changes to domains
 *                             to other parts of the system.
 */
private final class DomainStoreActor(context: ActorContext[DomainStoreActor.Message],
                                     domainStore: DomainStore,
                                     configStore: ConfigStore,
                                     roleStore: RoleStore,
                                     favoriteDomainStore: UserFavoriteDomainStore,
                                     deltaLogStore: DomainSchemaDeltaLogStore,
                                     versionLogStore: DomainSchemaVersionLogStore,
                                     domainCreator: DomainCreator,
                                     domainDbManagerActor: ActorRef[DomainDatabaseManagerActor.Message],
                                     domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends AbstractBehavior[DomainStoreActor.Message](context) with Logging {

  import DomainStoreActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.system.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system
  private[this] val domainDbDeletionTimeout = Timeout(
    context.system.settings.config.getDuration("convergence.persistence.domain-databases.deletion-timeout").toNanos,
    TimeUnit.NANOSECONDS
  )

  context.system.receptionist ! Receptionist.Register(DomainStoreActor.Key, context.self)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: CreateDomainRequest =>
        onCreateDomain(message)
      case message: DeleteDomainRequest =>
        onDeleteDomain(message)
      case message: UpdateDomainRequest =>
        onUpdateDomain(message)
      case message: GetDomainRequest =>
        onGetDomain(message)
      case message: GetDomainAndSchemaVersionRequest =>
        onGetDomainAndSchemaVersion(message)
      case message: GetDomainsRequest =>
        onGetDomains(message)
      case message: DeleteDomainsForUserRequest =>
        onDeleteDomainsForUser(message)
      case message: SetDomainAvailabilityRequest =>
        onSetDomainAvailability(message)
      case message: SetDomainIdRequest =>
        onSetDomainId(message)
    }

    Behaviors.same
  }

  private[this] def onCreateDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, id, displayName, anonymousAuth, collectionConfig, owner, replyTo) = createRequest
    configStore.getConfigs(List(ConfigKeys.Namespaces.Enabled, ConfigKeys.Namespaces.DefaultNamespace))
    val domainId = DomainId(namespace, id)
    domainCreator.createDomain(domainId, displayName, owner)
      .map { dbInfo =>
        // This returns a future, but we don't need to take any
        // action.
        domainCreator.createDomainDatabase(domainId, anonymousAuth, collectionConfig, dbInfo)
        CreateDomainResponse(Right(dbInfo))
      }
      .recover {
        case DomainCreator.DomainAlreadyExists(field) =>
          CreateDomainResponse(Left(DomainAlreadyExistsError(field)))
        case DomainCreator.InvalidDomainValue(message) =>
          CreateDomainResponse(Left(InvalidDomainCreationRequest(message)))
        case DomainCreator.NamespaceNotFoundError() =>
          CreateDomainResponse(Left(InvalidDomainCreationRequest(s"The namespace '$namespace' does not exist.")))
        case DomainCreator.UnknownError() =>
          CreateDomainResponse(Left(UnknownError()))
        case cause =>
          error(s"Unexpected error creating domain: $domainId", cause)
          CreateDomainResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)

  }

  private[this] def onUpdateDomain(request: UpdateDomainRequest): Unit = {
    val UpdateDomainRequest(namespace, domainId, displayName, replyTo) = request

    for {
      domain <- domainStore.findDomain(DomainId(namespace, domainId))
      response <- domain match {
        case Some(domain) =>
          val updated = domain.copy(displayName = displayName)
          domainStore.updateDomain(updated)
            .map(_ => UpdateDomainResponse(Right(Ok())))
            .recover {
              case _: EntityNotFoundException =>
                UpdateDomainResponse(Left(DomainNotFound()))
              case DuplicateValueException(field, _, _) =>
                UpdateDomainResponse(Left(DomainAlreadyExistsError(field)))
              case _ =>
                UpdateDomainResponse(Left(UnknownError()))
            }
        case None =>
          Success(UpdateDomainResponse(Left(DomainNotFound())))
      }
    } yield {
      replyTo ! response
    }
  }

  private[this] def onDeleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId, replyTo) = deleteRequest
    val domainFqn = DomainId(namespace, domainId)
    deleteDomain(domainFqn)
      .map(_ => DeleteDomainResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteDomainResponse(Left(DomainNotFound()))
        case _ =>
          DeleteDomainResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def deleteDomain(domainId: DomainId): Try[Unit] = {
    (for {
      _ <- domainStore
        .setDomainStatus(domainId, DomainStatus.Deleting, "")
        .map { _ =>
          val message = DomainLifecycleTopic.DomainStatusChanged(domainId, DomainStatus.Deleting)
          domainLifecycleTopic ! Publish(message)
        }
      domainDatabase <- domainStore.findDomainDatabase(domainId)
      result <- domainDatabase match {
        case Some(domainDatabase) =>
          debug(s"Deleting domain database for $domainId: ${domainDatabase.database}")
          implicit val requestTimeout: Timeout = domainDbDeletionTimeout
          domainDbManagerActor.ask[DestroyDomainResponse](ref => DestroyDomainRequest(domainId, domainDatabase.database, ref)) onComplete {
            case Success(_) =>
              debug(s"Domain database deleted, deleting domain related records in convergence database: ${domainDatabase.database}")
              (for {
                _ <- deltaLogStore.removeDeltaLogForDomain(domainId)
                  .map(_ => debug(s"Domain database delta log removed: $domainId"))
                _ <- versionLogStore.removeVersionLogForDomain(domainId)
                  .map(_ => debug(s"Domain database version log removed: $domainId"))
                _ <- favoriteDomainStore.removeFavoritesForDomain(domainId)
                  .map(_ => debug(s"Favorites for Domain removed: $domainId"))
                _ <- roleStore.removeAllRolesFromTarget(DomainRoleTarget(domainId))
                  .map(_ => debug(s"Domain record removed: $domainId"))
                _ <- domainStore.removeDomain(domainId)
                  .map(_ => debug(s"Domain record removed: $domainId"))
              } yield {
                ()
              }).recoverWith {
                case cause: Throwable =>
                  error(s"Could not delete domain: $domainId", cause)
                  domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
              }
            case Failure(cause) =>
              error(s"Could not destroy domain: $domainId", cause)
              domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
          }
          Success(())
        case None =>
          Failure(EntityNotFoundException(s"Could not find domain information to delete the domain: $domainId"))
      }
    } yield result)
      .recoverWith {
        case cause: Throwable =>
          domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
          Failure(cause)
      }
  }

  private[this] def onDeleteDomainsForUser(request: DeleteDomainsForUserRequest): Unit = {
    val DeleteDomainsForUserRequest(username, replyTo) = request
    debug(s"Deleting domains for user: $username")
    domainStore.getDomainsInNamespace("~" + username)
      .map { domains =>
        // FIXME we need to review what happens when something fails.
        //  we will eventually delete the user and then we won't be
        //  able to look up the domains again.
        domains.foreach { domain =>
          deleteDomain(domain.domainId) recover {
            case cause: Exception =>
              error(s"Unable to delete domain '${domain.domainId}' while deleting user '$username'", cause)
          }
        }
        DeleteDomainsForUserResponse(Right(Ok()))
      }
      .recover {
        case cause: Exception =>
          error(s"Error deleting domains for user: $username", cause)
          DeleteDomainsForUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId, replyTo) = getRequest
    domainStore
      .findDomain(DomainId(namespace, domainId))
      .map {
        case Some(domain) =>
          GetDomainResponse(Right(domain))
        case None =>
          GetDomainResponse(Left(DomainNotFound()))
      }
      .recover {
        case cause =>
          error(s"An unexpected error occurred while getting a domain: $domainId", cause)
          GetDomainResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetDomainAndSchemaVersion(getRequest: GetDomainAndSchemaVersionRequest): Unit = {
    val GetDomainAndSchemaVersionRequest(namespace, domainId, replyTo) = getRequest
    val id = DomainId(namespace, domainId)

    (for {
      domain <- domainStore.findDomain(id)
      database <- domainStore.getDomainDatabase(id)
    } yield {
      domain match {
        case Some(domain) =>
          Right(DomainAndSchemaVersion(domain, database.schemaVersion))
        case None =>
          Left(DomainNotFound())
      }
    })
      .recover {
        case _: EntityNotFoundException =>
          Left(DomainNotFound())
        case cause =>
          error(s"An unexpected error occurred while getting a domain: $domainId", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetDomainAndSchemaVersionResponse(_))
  }

  private[this] def onGetDomains(listRequest: GetDomainsRequest): Unit = {
    val GetDomainsRequest(authProfileData, namespace, filter, offset, limit, replyTo) = listRequest
    val authProfile = AuthorizationProfile(authProfileData)
    val result = if (authProfile.hasGlobalPermission(Permissions.Server.ManageDomains)) {
      domainStore.getDomains(namespace, filter, offset, limit)
    } else {
      domainStore.getDomainsByAccess(authProfile.username, namespace, filter, offset, limit)
    }

    result
      .map(domains => GetDomainsResponse(Right(domains)))
      .recover { cause =>
        error("unexpected error getting domains", cause)
        GetDomainsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  def onSetDomainAvailability(msg: SetDomainAvailabilityRequest): Unit = {
    val SetDomainAvailabilityRequest(domainId, availability, replyTo) = msg
    domainStore.setDomainAvailability(domainId, availability)
      .map { _ =>
        domainLifecycleTopic ! Publish(DomainAvailabilityChanged(domainId, availability))
        Right(Ok())
      }
      .recover {
        case _: EntityNotFoundException =>
          Left(DomainNotFound())
        case _: InvalidStatusException =>
          Left(InvalidDomainAvailability())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! SetDomainAvailabilityResponse(_))
  }

  def onSetDomainId(msg: SetDomainIdRequest): Unit = {
    val SetDomainIdRequest(domainId, id, replyTo) = msg
    (for {
      domain <- domainStore.getDomain(domainId)
      response <- if (domain.availability == DomainAvailability.Offline) {
        domainStore.setDomainId(domainId, id).map(_ => Right(Ok()))
      } else {
        Success(Left(DomainNotOfflineError()))
      }
    } yield response)
      .recover {
        case _: EntityNotFoundException =>
          Left(DomainNotFound())
        case e: DuplicateValueException =>
          Left(DomainAlreadyExistsError(e.field))
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! SetDomainIdResponse(_))
  }
}

object DomainStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("DomainStoreActor")

  def apply(domainStore: DomainStore,
            configStore: ConfigStore,
            roleStore: RoleStore,
            favoriteDomainStore: UserFavoriteDomainStore,
            deltaLogStore: DomainSchemaDeltaLogStore,
            versionLogStore: DomainSchemaVersionLogStore,
            domainCreator: DomainCreator,
            domainDatabaseManager: ActorRef[DomainDatabaseManagerActor.Message],
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup(context => new DomainStoreActor(
      context,
      domainStore,
      configStore,
      roleStore,
      favoriteDomainStore,
      deltaLogStore,
      versionLogStore,
      domainCreator,
      domainDatabaseManager,
      domainLifecycleTopic))

  private case class InvalidStatusException() extends Exception

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // CreateDomain
  //
  final case class CreateDomainRequest(namespace: String,
                                       domainId: String,
                                       displayName: String,
                                       anonymousAuth: Boolean,
                                       collectionConfig: CollectionConfig,
                                       owner: String,
                                       replyTo: ActorRef[CreateDomainResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainAlreadyExistsError], name = "domain_exists"),
    new JsonSubTypes.Type(value = classOf[InvalidDomainCreationRequest], name = "invalid_request"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateDomainError

  final case class InvalidDomainCreationRequest(message: String) extends CreateDomainError

  final case class CreateDomainResponse(dbInfo: Either[CreateDomainError, DomainDatabase]) extends CborSerializable

  //
  // UpdateDomain
  //
  final case class UpdateDomainRequest(namespace: String, domainId: String, displayName: String, replyTo: ActorRef[UpdateDomainResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainAlreadyExistsError], name = "domain_exists"),
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateDomainError

  final case class UpdateDomainResponse(response: Either[UpdateDomainError, Ok]) extends CborSerializable

  //
  // DeleteDomain
  //
  final case class DeleteDomainRequest(namespace: String, domainId: String, replyTo: ActorRef[DeleteDomainResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteDomainError

  final case class DeleteDomainResponse(response: Either[DeleteDomainError, Ok]) extends CborSerializable

  //
  // SetDomainAvailability
  //
  final case class SetDomainAvailabilityRequest(domainId: DomainId,
                                                availability: DomainAvailability.Value,
                                                replyTo: ActorRef[SetDomainAvailabilityResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[InvalidDomainAvailability], name = "invalid_availability"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetDomainAvailabilityError

  final case class SetDomainAvailabilityResponse(response: Either[SetDomainAvailabilityError, Ok]) extends CborSerializable

  //
  // SetDomainId
  //
  final case class SetDomainIdRequest(domainId: DomainId,
                                      id: String,
                                      replyTo: ActorRef[SetDomainIdResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[DomainAlreadyExistsError], name = "domain_already_exists"),
    new JsonSubTypes.Type(value = classOf[DomainNotOfflineError], name = "not_offline"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetDomainIdError

  final case class DomainNotOfflineError() extends SetDomainIdError

  final case class SetDomainIdResponse(response: Either[SetDomainIdError, Ok]) extends CborSerializable


  //
  // DeleteDomainsForUser
  //
  final case class DeleteDomainsForUserRequest(username: String, replyTo: ActorRef[DeleteDomainsForUserResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteDomainsForUserError

  final case class DeleteDomainsForUserResponse(response: Either[DeleteDomainsForUserError, Ok]) extends CborSerializable

  //
  // GetDomain
  //
  final case class GetDomainRequest(namespace: String, domainId: String, replyTo: ActorRef[GetDomainResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainError

  final case class GetDomainResponse(domain: Either[GetDomainError, Domain]) extends CborSerializable

  //
  // GetDomainAndSchemaVersion
  //
  final case class GetDomainAndSchemaVersionRequest(namespace: String, domainId: String, replyTo: ActorRef[GetDomainAndSchemaVersionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainAndSchemaVersionError

  final case class DomainAndSchemaVersion(domain: Domain, schemaVersion: String)

  final case class GetDomainAndSchemaVersionResponse(domain: Either[GetDomainAndSchemaVersionError, DomainAndSchemaVersion]) extends CborSerializable


  //
  // GetDomains
  //
  final case class GetDomainsRequest(authProfile: AuthorizationProfileData,
                                     namespace: Option[String],
                                     filter: Option[String],
                                     @JsonDeserialize(contentAs = classOf[Long])
                                     offset: QueryOffset,
                                     @JsonDeserialize(contentAs = classOf[Long])
                                     limit: QueryLimit,
                                     replyTo: ActorRef[GetDomainsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainsError extends CborSerializable

  final case class GetDomainsResponse(domains: Either[GetDomainsError, List[Domain]]) extends CborSerializable

  //
  // Common Errors
  //

  final case class DomainAlreadyExistsError(field: String) extends AnyRef
    with CreateDomainError
    with UpdateDomainError
    with SetDomainIdError

  final case class DomainNotFound() extends AnyRef
    with UpdateDomainError
    with DeleteDomainError
    with GetDomainError
    with GetDomainAndSchemaVersionError
    with SetDomainAvailabilityError
    with SetDomainIdError

  final case class InvalidDomainAvailability() extends AnyRef
    with SetDomainAvailabilityError

  final case class UnknownError() extends AnyRef
    with CreateDomainError
    with UpdateDomainError
    with DeleteDomainError
    with GetDomainError
    with GetDomainsError
    with DeleteDomainsForUserError
    with GetDomainAndSchemaVersionError
    with SetDomainAvailabilityError
    with SetDomainIdError
}
