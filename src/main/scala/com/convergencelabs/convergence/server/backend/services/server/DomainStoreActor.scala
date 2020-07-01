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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.backend.db.provision.DomainProvisionerActor
import com.convergencelabs.convergence.server.backend.db.provision.DomainProvisionerActor.{DestroyDomain, DestroyDomainResponse, ProvisionDomain, ProvisionDomainResponse}
import com.convergencelabs.convergence.server.backend.services.domain.Domain
import com.convergencelabs.convergence.server.model.domain.{DomainDatabase, DomainId, DomainStatus}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class DomainStoreActor private(context: ActorContext[DomainStoreActor.Message],
                               domainStore: DomainStore,
                               configStore: ConfigStore,
                               roleStore: RoleStore,
                               favoriteDomainStore: UserFavoriteDomainStore,
                               deltaHistoryStore: DeltaHistoryStore,
                               domainCreator: DomainCreator,
                               domainProvisioner: ActorRef[DomainProvisionerActor.Message])
  extends AbstractBehavior[DomainStoreActor.Message](context) with Logging {

  import DomainStoreActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.system.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  context.system.receptionist ! Receptionist.Register(DomainStoreActor.Key, context.self)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: CreateDomainRequest =>
        createDomain(message)
      case message: DeleteDomainRequest =>
        deleteDomain(message)
      case message: UpdateDomainRequest =>
        updateDomain(message)
      case message: GetDomainRequest =>
        handleGetDomain(message)
      case message: GetDomainsRequest =>
        onGetDomains(message)
      case message: DeleteDomainsForUserRequest =>
        deleteDomainsForUser(message)
    }

    Behaviors.same
  }

  private[this] def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, id, displayName, anonymousAuth, owner, replyTo) = createRequest
    configStore.getConfigs(List(ConfigKeys.Namespaces.Enabled, ConfigKeys.Namespaces.DefaultNamespace))
    val domainId = DomainId(namespace, id)
    domainCreator.createDomain(domainId, displayName, owner)
      .map { dbInfo =>
        // This returns a future, but we don't need to take any
        // action.
        domainCreator.provisionDomain(domainId, anonymousAuth, dbInfo)
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

  private[this] def updateDomain(request: UpdateDomainRequest): Unit = {
    val UpdateDomainRequest(namespace, domainId, displayName, replyTo) = request

    for {
      domain <- domainStore.getDomainByFqn(DomainId(namespace, domainId))
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

  private[this] def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
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
      _ <- domainStore.setDomainStatus(domainId, DomainStatus.Deleting, "")
      domainDatabase <- domainStore.getDomainDatabase(domainId)
      result <- domainDatabase match {
        case Some(domainDatabase) =>
          debug(s"Deleting domain database for $domainId: ${domainDatabase.database}")
          // FIXME hard-coded timeout
          implicit val requestTimeout: Timeout = Timeout(4 minutes)
          domainProvisioner.ask[DestroyDomainResponse](ref => DestroyDomain(domainId, domainDatabase.database, ref)) onComplete {
            case Success(_) =>
              debug(s"Domain database deleted, deleting domain related records in convergence database: ${domainDatabase.database}")
              (for {
                _ <- deltaHistoryStore.removeDeltaHistoryForDomain(domainId)
                  .map(_ => debug(s"Domain database delta history removed: $domainId"))
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

  private[this] def deleteDomainsForUser(request: DeleteDomainsForUserRequest): Unit = {
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

  private[this] def handleGetDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId, replyTo) = getRequest
    domainStore
      .getDomainByFqn(DomainId(namespace, domainId))
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
}

class ActorBasedDomainCreator(databaseProvider: DatabaseProvider,
                              config: Config,
                              domainProvisioner: ActorRef[ProvisionDomain],
                              executionContext: ExecutionContext,
                              implicit val scheduler: Scheduler,
                              timeout: Timeout)
  extends DomainCreator(databaseProvider, config, executionContext) {

  def provisionDomain(data: ProvisionRequest): Future[ProvisionDomainResponse] = {
    implicit val t: Timeout = timeout
    domainProvisioner
      .ask[ProvisionDomainResponse](ref => ProvisionDomain(data, ref))
  }
}

object DomainStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("DomainStoreActor")

  def apply(domainStore: DomainStore,
            configStore: ConfigStore,
            roleStore: RoleStore,
            favoriteDomainStore: UserFavoriteDomainStore,
            deltaHistoryStore: DeltaHistoryStore,
            domainCreator: DomainCreator,
            provisionerActor: ActorRef[DomainProvisionerActor.Message]): Behavior[Message] =
    Behaviors.setup(context => new DomainStoreActor(
      context, domainStore, configStore, roleStore, favoriteDomainStore, deltaHistoryStore, domainCreator, provisionerActor))

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

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteDomainError

  final case class DeleteDomainResponse(response: Either[DeleteDomainError, Ok]) extends CborSerializable

  //
  // DeleteDomainsForUser
  //
  final case class DeleteDomainsForUserRequest(username: String, replyTo: ActorRef[DeleteDomainsForUserResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteDomainsForUserError

  final case class DeleteDomainsForUserResponse(response: Either[DeleteDomainsForUserError, Ok]) extends CborSerializable

  //
  // GetDomain
  //
  final case class GetDomainRequest(namespace: String, domainId: String, replyTo: ActorRef[GetDomainResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[DomainNotFound], name = "domain_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainError

  final case class GetDomainResponse(domain: Either[GetDomainError, Domain]) extends CborSerializable

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

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetDomainsError extends CborSerializable

  final case class GetDomainsResponse(domains: Either[GetDomainsError, List[Domain]])

  //
  // Common Errors
  //

  final case class DomainAlreadyExistsError(field: String) extends AnyRef
    with CreateDomainError
    with UpdateDomainError

  final case class DomainNotFound() extends AnyRef
    with UpdateDomainError
    with DeleteDomainError
    with GetDomainError

  final case class UnknownError() extends AnyRef
    with CreateDomainError
    with UpdateDomainError
    with DeleteDomainError
    with GetDomainError
    with GetDomainsError
    with DeleteDomainsForUserError

}
