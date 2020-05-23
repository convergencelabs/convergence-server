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

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.{InvalidValueException, StoreActor}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{Namespace, NamespaceAndDomains, NamespaceUpdates}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object NamespaceStoreActor {
  val RelativePath = "NamespaceStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new NamespaceStoreActor(dbProvider))

  sealed trait NamespaceStoreActorRequest extends CborSerializable

  case class CreateNamespaceRequest(requester: String, namespaceId: String, displayName: String) extends NamespaceStoreActorRequest

  case class UpdateNamespaceRequest(requester: String, namespaceId: String, displayName: String) extends NamespaceStoreActorRequest

  case class DeleteNamespaceRequest(requester: String, namespaceId: String) extends NamespaceStoreActorRequest

  case class GetAccessibleNamespacesRequest(requester: AuthorizationProfileData, filter: Option[String], offset: Option[Int], limit: Option[Int]) extends NamespaceStoreActorRequest

  case class GetAccessibleNamespacesResponse(namespaces: Set[NamespaceAndDomains]) extends CborSerializable

  case class GetNamespaceRequest(namespaceId: String) extends NamespaceStoreActorRequest

  case class GetNamespaceResponse(namespace: Option[Namespace]) extends CborSerializable

}

class NamespaceStoreActor private[datastore](
                                              private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import NamespaceStoreActor._

  private[this] val namespaceStore = new NamespaceStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)

  def receive: Receive = {
    case createRequest: CreateNamespaceRequest =>
      createNamespace(createRequest)
    case deleteRequest: DeleteNamespaceRequest =>
      deleteNamespace(deleteRequest)
    case updateRequest: UpdateNamespaceRequest =>
      updateNamespace(updateRequest)
    case getRequest: GetNamespaceRequest =>
      handleGetNamespace(getRequest)
    case accessibleRequest: GetAccessibleNamespacesRequest =>
      handleGetAccessibleNamespaces(accessibleRequest)
    case message: Any =>
      unhandled(message)
  }

  private[this] def createNamespace(createRequest: CreateNamespaceRequest): Unit = {
    val CreateNamespaceRequest(_, namespaceId, displayName) = createRequest

    reply(this.validate(namespaceId, displayName)
      .flatMap(_ => namespaceStore.createNamespace(namespaceId, displayName, userNamespace = false)))
  }

  private[this] def handleGetNamespace(getRequest: GetNamespaceRequest): Unit = {
    val GetNamespaceRequest(namespaceId) = getRequest
    reply(namespaceStore.getNamespace(namespaceId).map(GetNamespaceResponse))
  }

  private[this] def updateNamespace(request: UpdateNamespaceRequest): Unit = {
    val UpdateNamespaceRequest(_, namespaceId, displayName) = request
    reply(namespaceStore.updateNamespace(NamespaceUpdates(namespaceId, displayName)))
  }

  private[this] def deleteNamespace(deleteRequest: DeleteNamespaceRequest): Unit = {
    val DeleteNamespaceRequest(_, namespaceId) = deleteRequest
    log.debug("Delete Namespace: " + namespaceId)
    val result = for {
      _ <- roleStore.removeAllRolesFromTarget(NamespaceRoleTarget(namespaceId))
      _ <- namespaceStore.deleteNamespace(namespaceId)
    } yield ()
    reply(result)
  }

  private[this] def handleGetAccessibleNamespaces(getRequest: GetAccessibleNamespacesRequest): Unit = {
    val GetAccessibleNamespacesRequest(authProfileData, _, _, _) = getRequest
    val authProfile = AuthorizationProfile(authProfileData)
    if (authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)) {
      reply(namespaceStore.getAllNamespacesAndDomains().map(GetAccessibleNamespacesResponse))
    } else {
      reply(namespaceStore
        .getAccessibleNamespaces(authProfile.username)
        .flatMap(namespaces => namespaceStore.getNamespaceAndDomains(namespaces.map(_.id).toSet))
        .map(GetAccessibleNamespacesResponse))
    }
  }

  private[this] def validate(namespace: String, displayName: String): Try[Unit] = {
    if (namespace.isEmpty) {
      Failure(InvalidValueException("namespace", "The namespace can not be empty"))
    } else if (namespace.startsWith("~")) {
      Failure(InvalidValueException("namespace", "Normal namespaces can not being with a '~', as these are reserved for user namespaces"))
    } else if (displayName.isEmpty) {
      Failure(InvalidValueException("displayName", "The display name can not be empty."))
    } else {
      configStore.getConfig(ConfigKeys.Namespaces.Enabled).flatMap { config =>
        val namespacesEnabled = config.get.asInstanceOf[Boolean]
        if (!namespacesEnabled) {
          Failure(InvalidValueException("namespace", "Can not create the namespace because namespaces are disabled"))
        } else {
          Success(())
        }
      }
    }
  }
}
