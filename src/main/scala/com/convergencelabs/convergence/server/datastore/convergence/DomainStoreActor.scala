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

package com.convergencelabs.convergence.server.datastore.convergence

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{DestroyDomain, DestroyDomainResponse, ProvisionDomain, ProvisionDomainResponse}
import com.convergencelabs.convergence.server.domain.{Domain, DomainDatabase, DomainId, DomainStatus}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

private class DomainStoreActor private[datastore](private[this] val context: ActorContext[DomainStoreActor.Message],
                                          private[this] val dbProvider: DatabaseProvider,
                                          private[this] val domainProvisioner: ActorRef[DomainProvisionerActor.Message])
  extends AbstractBehavior[DomainStoreActor.Message](context) with Logging {

  import DomainStoreActor._

  context.system.receptionist ! Receptionist.Register(DomainStoreActor.Key, context.self)

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val favoriteDomainStore = new UserFavoriteDomainStore(dbProvider)
  private[this] val deltaHistoryStore: DeltaHistoryStore = new DeltaHistoryStore(dbProvider)
  private[this] implicit val ec: ExecutionContextExecutor = context.system.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system
  private[this] val domainCreator: DomainCreator = new ActorBasedDomainCreator(
    dbProvider,
    this.context.system.settings.config,
    domainProvisioner.narrow[ProvisionDomain],
    ec,
    system)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case createRequest: CreateDomainRequest =>
        createDomain(createRequest)
      case deleteRequest: DeleteDomainRequest =>
        deleteDomain(deleteRequest)
      case updateRequest: UpdateDomainRequest =>
        updateDomain(updateRequest)
      case getRequest: GetDomainRequest =>
        handleGetDomain(getRequest)
      case listRequest: ListDomainsRequest =>
        listDomains(listRequest)
      case deleteForUser: DeleteDomainsForUserRequest =>
        deleteDomainsForUser(deleteForUser)
    }

    Behaviors.same
  }

  private[this] def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, anonymousAuth, owner, replyTo) = createRequest
    configStore.getConfigs(List(ConfigKeys.Namespaces.Enabled, ConfigKeys.Namespaces.DefaultNamespace))
    domainCreator.createDomain(namespace, domainId, displayName, anonymousAuth, owner) onComplete {
      case Success(database) =>
        replyTo ! CreateDomainSuccess(database)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def updateDomain(request: UpdateDomainRequest): Unit = {
    val UpdateDomainRequest(namespace, domainId, displayName, replyTo) = request

    domainStore.getDomainByFqn(DomainId(namespace, domainId)).flatMap {
      case Some(domain) =>
        val updated = domain.copy(displayName = displayName)
        domainStore.updateDomain(updated)
      case None =>
        Failure(EntityNotFoundException())
    } match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId, replyTo) = deleteRequest
    val domainFqn = DomainId(namespace, domainId)
    deleteDomain(domainFqn) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
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

    domainStore.getDomainsInNamespace("~" + username) map { domains =>
      // FIXME we need to review what happens when something fails.
      //  we will eventually delete the user and then we won't be
      //  able to look up the domains again.
      replyTo ! RequestSuccess()

      domains.foreach { domain =>
        deleteDomain(domain.domainFqn) recover {
          case cause: Exception =>
            error(s"Unable to delete domain '${domain.domainFqn}' while deleting user '$username'", cause)
        }
      }
    } recover {
      case cause: Exception =>
        error(s"Error deleting domains for user: $username", cause)
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def handleGetDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId, replyTo) = getRequest
    domainStore.getDomainByFqn(DomainId(namespace, domainId)) match {
      case Success(domains) =>
        replyTo ! GetDomainSuccess(domains)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def listDomains(listRequest: ListDomainsRequest): Unit = {
    val ListDomainsRequest(authProfileData, namespace, filter, offset, limit, replyTo) = listRequest
    val authProfile = AuthorizationProfile(authProfileData)
    if (authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)) {
      domainStore.getDomains(namespace, filter, offset, limit)
    } else {
      domainStore.getDomainsByAccess(authProfile.username, namespace, filter, offset, limit)
    } match {
      case Success(domains) =>
        replyTo ! ListDomainsSuccess(domains)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}

class ActorBasedDomainCreator(databaseProvider: DatabaseProvider,
                              config: Config,
                              domainProvisioner: ActorRef[ProvisionDomain],
                              executionContext: ExecutionContext,
                              implicit val system: ActorSystem[_])
  extends DomainCreator(databaseProvider, config, executionContext) {

  def provisionDomain(data: ProvisionRequest): Future[Unit] = {
    implicit val t: Timeout = Timeout(4 minutes)
    domainProvisioner.ask[ProvisionDomainResponse](ref => ProvisionDomain(data, ref)).mapTo[Unit]
  }
}


object DomainStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("DomainStoreActor")

  def apply(dbProvider: DatabaseProvider, provisionerActor: ActorRef[DomainProvisionerActor.Message]): Behavior[Message] =
    Behaviors.setup(context => new DomainStoreActor(context, dbProvider, provisionerActor))


  sealed trait Message extends CborSerializable

  //
  // CreateDomain
  //
  case class CreateDomainRequest(namespace: String,
                                 domainId: String,
                                 displayName: String,
                                 anonymousAuth: Boolean,
                                 owner: String,
                                 replyTo: ActorRef[CreateDomainResponse]) extends Message

  sealed trait CreateDomainResponse extends CborSerializable

  case class CreateDomainSuccess(dbInfo: DomainDatabase) extends CreateDomainResponse

  //
  // UpdateDomain
  //
  case class UpdateDomainRequest(namespace: String, domainId: String, displayName: String, replyTo: ActorRef[UpdateDomainResponse]) extends Message

  sealed trait UpdateDomainResponse extends CborSerializable

  //
  // DeleteDomain
  //
  case class DeleteDomainRequest(namespace: String, domainId: String, replyTo: ActorRef[DeleteDomainResponse]) extends Message

  sealed trait DeleteDomainResponse extends CborSerializable

  //
  // DeleteDomainsForUser
  //
  case class DeleteDomainsForUserRequest(username: String, replyTo: ActorRef[DeleteDomainsForUserResponse]) extends Message

  sealed trait DeleteDomainsForUserResponse extends CborSerializable

  //
  // GetDomain
  //
  case class GetDomainRequest(namespace: String, domainId: String, replyTo: ActorRef[GetDomainResponse]) extends Message

  sealed trait GetDomainResponse extends CborSerializable

  case class GetDomainSuccess(domain: Option[Domain]) extends GetDomainResponse

  //
  // ListDomains
  //
  case class ListDomainsRequest(authProfile: AuthorizationProfileData,
                                namespace: Option[String],
                                filter: Option[String],
                                offset: Option[Int],
                                limit: Option[Int],
                                replyTo: ActorRef[ListDomainsResponse]) extends Message

  sealed trait ListDomainsResponse extends CborSerializable

  case class ListDomainsSuccess(domains: List[Domain]) extends ListDomainsResponse


  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with CreateDomainResponse
    with UpdateDomainResponse
    with DeleteDomainResponse
    with DeleteDomainsForUserResponse
    with GetDomainResponse
    with ListDomainsResponse


  case class RequestSuccess() extends CborSerializable
    with UpdateDomainResponse
    with DeleteDomainResponse
    with DeleteDomainsForUserResponse

}
