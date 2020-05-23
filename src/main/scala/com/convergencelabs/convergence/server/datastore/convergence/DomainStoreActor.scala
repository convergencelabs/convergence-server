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

import akka.actor.{ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.{EntityNotFoundException, StoreActor}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainProvisionerActor.{DestroyDomain, ProvisionDomain}
import com.convergencelabs.convergence.server.domain.{Domain, DomainId, DomainStatus}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import com.typesafe.config.Config

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class DomainStoreActor private[datastore](
                                           private[this] val dbProvider: DatabaseProvider,
                                           private[this] val domainProvisioner: ActorRef)
  extends StoreActor with ActorLogging {

  import DomainStoreActor._

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val favoriteDomainStore = new UserFavoriteDomainStore(dbProvider)
  private[this] val deltaHistoryStore: DeltaHistoryStore = new DeltaHistoryStore(dbProvider)
  private[this] implicit val ec: ExecutionContextExecutor = context.system.dispatcher
  private[this] val domainCreator: DomainCreator = new ActorBasedDomainCreator(
    dbProvider,
    this.context.system.settings.config,
    domainProvisioner,
    ec)

  def receive: Receive = {
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
    case message: Any => unhandled(message)
  }

  private[this] def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, anonymousAuth, owner) = createRequest
    configStore.getConfigs(List(ConfigKeys.Namespaces.Enabled, ConfigKeys.Namespaces.DefaultNamespace))
    reply(domainCreator.createDomain(namespace, domainId, displayName, anonymousAuth, owner).map(_ => ()))
  }

  private[this] def updateDomain(request: UpdateDomainRequest): Unit = {
    val UpdateDomainRequest(namespace, domainId, displayName) = request
    reply(
      domainStore.getDomainByFqn(DomainId(namespace, domainId)).flatMap {
        case Some(domain) =>
          val updated = domain.copy(displayName = displayName)
          domainStore.updateDomain(updated)
        case None =>
          Failure(EntityNotFoundException())
      })
  }

  private[this] def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId) = deleteRequest
    val domainFqn = DomainId(namespace, domainId)
    reply(deleteDomain(domainFqn))
  }

  private[this] def deleteDomain(domainId: DomainId): Try[Unit] = {
    (for {
      _ <- domainStore.setDomainStatus(domainId, DomainStatus.Deleting, "")
      domainDatabase <- domainStore.getDomainDatabase(domainId)
      result <- domainDatabase match {
        case Some(domainDatabase) =>
          log.debug(s"Deleting domain database for $domainId: ${domainDatabase.database}")
          implicit val requestTimeout: Timeout = Timeout(4 minutes) // FXIME hard-coded timeout
          (domainProvisioner ? DestroyDomain(domainId, domainDatabase.database)) onComplete {
            case Success(_) =>
              log.debug(s"Domain database deleted, deleting domain related records in convergence database: ${domainDatabase.database}")
              (for {
                _ <- deltaHistoryStore.removeDeltaHistoryForDomain(domainId)
                  .map(_ => log.debug(s"Domain database delta history removed: $domainId"))
                _ <- favoriteDomainStore.removeFavoritesForDomain(domainId)
                  .map(_ => log.debug(s"Favorites for Domain removed: $domainId"))
                _ <- roleStore.removeAllRolesFromTarget(DomainRoleTarget(domainId))
                  .map(_ => log.debug(s"Domain record removed: $domainId"))
                _ <- domainStore.removeDomain(domainId)
                  .map(_ => log.debug(s"Domain record removed: $domainId"))
              } yield {
                ()
              }).recoverWith {
                case cause: Throwable =>
                  log.error(cause, s"Could not delete domain: $domainId")
                  domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
              }
            case Failure(cause) =>
              log.error(cause, s"Could not destroy domain: $domainId")
              domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
          }
          Success(())
        case None =>
          Failure(EntityNotFoundException(s"Could not find domain information to delete the domain: $domainId"))
      }
    } yield {
      result
    }).recoverWith {
      case cause: Throwable =>
        domainStore.setDomainStatus(domainId, DomainStatus.Error, "There was an unexpected error deleting the domain")
        Failure(cause)
    }
  }

  private[this] def deleteDomainsForUser(request: DeleteDomainsForUserRequest): Unit = {
    val DeleteDomainsForUserRequest(username) = request
    log.debug(s"Deleting domains for user: $username")

    domainStore.getDomainsInNamespace("~" + username) map { domains =>
      // FIXME we need to review what happens when something fails.
      //  we will eventually delete the user and then we won't be
      //  able to look up the domains again.
      sender ! (())

      domains.foreach { domain =>
        deleteDomain(domain.domainFqn) recover {
          case cause: Exception =>
            log.error(cause, s"Unable to delete domain '${domain.domainFqn}' while deleting user '$username'")
        }
      }
    } recover {
      case cause: Exception =>
        log.error(cause, s"Error deleting domains for user: $username")
    }
  }

  private[this] def handleGetDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId) = getRequest
    reply(domainStore.getDomainByFqn(DomainId(namespace, domainId)).map(GetDomainResponse))
  }

  private[this] def listDomains(listRequest: ListDomainsRequest): Unit = {
    val ListDomainsRequest(authProfileData, namespace, filter, offset, limit) = listRequest
    val authProfile = AuthorizationProfile(authProfileData)
    if (authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)) {
      reply(domainStore.getDomains(namespace, filter, offset, limit).map(ListDomainsResponse))
    } else {
      reply(domainStore.getDomainsByAccess(authProfile.username, namespace, filter, offset, limit).map(ListDomainsResponse))
    }
  }
}

class ActorBasedDomainCreator(databaseProvider: DatabaseProvider, config: Config, domainProvisioner: ActorRef, executionContext: ExecutionContext)
  extends DomainCreator(databaseProvider, config, executionContext) {

  import akka.pattern.ask

  def provisionDomain(request: ProvisionDomain): Future[Unit] = {
    implicit val t: Timeout = Timeout(4 minutes)
    domainProvisioner.ask(request).mapTo[Unit]
  }
}


object DomainStoreActor {
  val RelativePath = "DomainStoreActor"

  def props(dbProvider: DatabaseProvider,
            provisionerActor: ActorRef): Props =
    Props(new DomainStoreActor(dbProvider, provisionerActor))

  sealed trait DomainStoreActorRequest extends CborSerializable

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, anonymousAuth: Boolean, owner: String) extends DomainStoreActorRequest

  case class UpdateDomainRequest(namespace: String, domainId: String, displayName: String) extends DomainStoreActorRequest

  case class DeleteDomainRequest(namespace: String, domainId: String) extends DomainStoreActorRequest

  case class DeleteDomainsForUserRequest(username: String) extends DomainStoreActorRequest

  case class GetDomainRequest(namespace: String, domainId: String) extends DomainStoreActorRequest

  case class GetDomainResponse(domain: Option[Domain]) extends CborSerializable

  case class ListDomainsRequest(authProfile: AuthorizationProfileData,
                                namespace: Option[String],
                                filter: Option[String],
                                offset: Option[Int],
                                limit: Option[Int]) extends DomainStoreActorRequest

  case class ListDomainsResponse(domains: List[Domain]) extends CborSerializable


}
