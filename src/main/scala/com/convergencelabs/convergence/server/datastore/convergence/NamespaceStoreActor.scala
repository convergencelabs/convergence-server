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

import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.util.ExceptionUtils
import com.convergencelabs.convergence.server.db.DatabaseProvider

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import com.convergencelabs.convergence.server.domain.Namespace
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.security.Permissions
import com.convergencelabs.convergence.server.domain.NamespaceUpdates
import com.convergencelabs.convergence.server.datastore.InvalidValueExcpetion

object NamespaceStoreActor {
  val RelativePath = "NamespaceStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new NamespaceStoreActor(dbProvider))

  case class CreateNamespace(requestor: String, namespaceId: String, displayName: String)
  case class UpdateNamespace(requestor: String, namespaceId: String, displayName: String)
  case class DeleteNamespace(requestor: String, namespaceId: String)
  case class GetAccessibleNamespaces(requestor: AuthorizationProfile, filter: Option[String], offset: Option[Int], limit: Option[Int])
  case class GetNamespace(namespaceId: String)
}

class NamespaceStoreActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import NamespaceStoreActor._
  import akka.pattern.ask

  private[this] val namespaceStore = new NamespaceStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)

  def receive: Receive = {
    case createRequest: CreateNamespace =>
      createNamespace(createRequest)
    case deleteRequest: DeleteNamespace =>
      deleteNamespace(deleteRequest)
    case updateRequest: UpdateNamespace =>
      updateNamespace(updateRequest)
    case getRequest: GetNamespace =>
      getNamespace(getRequest)
    case accessibleRequest: GetAccessibleNamespaces =>
      getAccessibleNamespaces(accessibleRequest)
    case message: Any =>
      unhandled(message)
  }

  def createNamespace(createRequest: CreateNamespace): Unit = {
    val CreateNamespace(requestor, namespaceId, displayName) = createRequest
    
    reply(this.validate(namespaceId, displayName)
    .flatMap( _ => namespaceStore.createNamespace(namespaceId, displayName, false)))
  }

  def getNamespace(getRequest: GetNamespace): Unit = {
    val GetNamespace(namespaceId) = getRequest
    reply(namespaceStore.getNamespace(namespaceId))
  }

  def updateNamespace(request: UpdateNamespace): Unit = {
    val UpdateNamespace(requestor, namespaceId, displayName) = request
    reply(namespaceStore.updateNamespace(NamespaceUpdates(namespaceId, displayName)))
  }

  def deleteNamespace(deleteRequest: DeleteNamespace): Unit = {
    val DeleteNamespace(requestor, namespaceId) = deleteRequest
    log.debug("Delete Namespace: " + namespaceId)
    reply(namespaceStore.deleteNamespace(namespaceId))
  }

  def getAccessibleNamespaces(getRequest: GetAccessibleNamespaces): Unit = {
    val GetAccessibleNamespaces(authProfile, filter, offset, limit) = getRequest
    if (authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)) {
      reply(namespaceStore.getAllNamespacesAndDomains())
    } else {
      reply(namespaceStore
        .getAccessibleNamespaces(authProfile.username)
        .flatMap(namespaces => namespaceStore.getNamespaceAndDomains(namespaces.map(_.id).toSet)))
    }
  }
  
  private[this] def validate(namespace: String, displayName: String): Try[Unit] = {
    if (namespace.isEmpty) {
      Failure(InvalidValueExcpetion("namespace", "The namespace can not be empty"))
    } else if (namespace.startsWith("~")) {
      Failure(InvalidValueExcpetion("namespace" ,"Normal namespaces can not being with a '~', as these are reserved for user namespaces"))
    } else if (displayName.isEmpty) {
      Failure(InvalidValueExcpetion("displayName","The display name can not be empty."))
    } else {
      configStore.getConfig(ConfigKeys.Namespaces.Enabled).flatMap { config =>
        val namespacesEnabled = config.get.asInstanceOf[Boolean]
        if (!namespacesEnabled) {
          Failure(InvalidValueExcpetion("namespace", "Can not create the namespace because namespaces are disabled"))
        } else {
          Success(())
        }
      }
    }
  }
}
