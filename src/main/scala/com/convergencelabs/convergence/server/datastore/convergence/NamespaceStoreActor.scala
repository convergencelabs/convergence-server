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
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.InvalidValueException
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{Namespace, NamespaceAndDomains, NamespaceUpdates}
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import grizzled.slf4j.Logging

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

private class NamespaceStoreActor (private[this] val context: ActorContext[NamespaceStoreActor.Message],
                                             private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[NamespaceStoreActor.Message](context) with Logging {

  import NamespaceStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val namespaceStore = new NamespaceStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)

  override def onMessage(msg: Message): Behavior[Message] = {
   msg match {
     case msg: CreateNamespaceRequest =>
       onCreateNamespace(msg)
     case msg: DeleteNamespaceRequest =>
       onDeleteNamespace(msg)
     case msg: UpdateNamespaceRequest =>
       onUpdateNamespace(msg)
     case msg: GetNamespaceRequest =>
       onGetNamespace(msg)
     case msg: GetAccessibleNamespacesRequest =>
      onGetAccessibleNamespaces(msg)
   }

   Behaviors.same
  }

  private[this] def onCreateNamespace(createRequest: CreateNamespaceRequest): Unit = {
    val CreateNamespaceRequest(_, namespaceId, displayName, replyTo) = createRequest

    this.validate(namespaceId, displayName)
      .flatMap(_ => namespaceStore.createNamespace(namespaceId, displayName, userNamespace = false)) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetNamespace(getRequest: GetNamespaceRequest): Unit = {
    val GetNamespaceRequest(namespaceId, replyTo) = getRequest
    namespaceStore.getNamespace(namespaceId) match {
      case Success(namespace) =>
        replyTo ! GetNamespaceSuccess(namespace)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateNamespace(request: UpdateNamespaceRequest): Unit = {
    val UpdateNamespaceRequest(_, namespaceId, displayName, replyTo) = request
    namespaceStore.updateNamespace(NamespaceUpdates(namespaceId, displayName)) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteNamespace(deleteRequest: DeleteNamespaceRequest): Unit = {
    val DeleteNamespaceRequest(_, namespaceId, replyTo) = deleteRequest
    debug("Delete Namespace: " + namespaceId)
    (for {
      _ <- roleStore.removeAllRolesFromTarget(NamespaceRoleTarget(namespaceId))
      _ <- namespaceStore.deleteNamespace(namespaceId)
    } yield ()) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetAccessibleNamespaces(getRequest: GetAccessibleNamespacesRequest): Unit = {
    val GetAccessibleNamespacesRequest(authProfileData, _, _, _, replyTo) = getRequest
    val authProfile = AuthorizationProfile(authProfileData)
    if (authProfile.hasGlobalPermission(Permissions.Global.ManageDomains)) {
      namespaceStore.getAllNamespacesAndDomains()
    } else {
      namespaceStore
        .getAccessibleNamespaces(authProfile.username)
        .flatMap(namespaces => namespaceStore.getNamespaceAndDomains(namespaces.map(_.id).toSet))
    } match {
      case Success(namespaces) =>
        replyTo ! GetAccessibleNamespacesSuccess(namespaces)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
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


object NamespaceStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("NamespaceStore")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] =
    Behaviors.setup(context => new NamespaceStoreActor(context, dbProvider))

  sealed trait Message extends CborSerializable

  //
  // CreateNamespace
  //
  case class CreateNamespaceRequest(requester: String, namespaceId: String, displayName: String, replyTo: ActorRef[CreateNamespaceResponse]) extends Message

  sealed trait CreateNamespaceResponse extends CborSerializable

  //
  // CreateNamespace
  //
  case class UpdateNamespaceRequest(requester: String, namespaceId: String, displayName: String, replyTo: ActorRef[UpdateNamespaceResponse]) extends Message

  sealed trait UpdateNamespaceResponse extends CborSerializable

  //
  // CreateNamespace
  //
  case class DeleteNamespaceRequest(requester: String, namespaceId: String, replyTo: ActorRef[DeleteNamespaceResponse]) extends Message

  sealed trait DeleteNamespaceResponse extends CborSerializable

  //
  // GetAccessibleNamespacesRequest
  //
  case class GetAccessibleNamespacesRequest(requester: AuthorizationProfileData,
                                            filter: Option[String],
                                            offset: Option[Int],
                                            limit: Option[Int],
                                            replyTo: ActorRef[GetAccessibleNamespacesResponse]) extends Message

  sealed trait GetAccessibleNamespacesResponse extends CborSerializable

  case class GetAccessibleNamespacesSuccess(namespaces: Set[NamespaceAndDomains]) extends GetAccessibleNamespacesResponse

  //
  // GetNamespace
  //
  case class GetNamespaceRequest(namespaceId: String, replyTo: ActorRef[GetNamespaceResponse]) extends Message

  sealed trait GetNamespaceResponse extends CborSerializable

  case class GetNamespaceSuccess(namespace: Option[Namespace]) extends GetNamespaceResponse

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with CreateNamespaceResponse
    with UpdateNamespaceResponse
    with DeleteNamespaceResponse
    with GetAccessibleNamespacesResponse
    with GetNamespaceResponse

  case class RequestSuccess() extends CborSerializable
    with CreateNamespaceResponse
    with UpdateNamespaceResponse
    with DeleteNamespaceResponse
}