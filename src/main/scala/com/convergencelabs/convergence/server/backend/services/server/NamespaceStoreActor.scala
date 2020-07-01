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
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.convergence.NamespaceStore.{NamespaceAndDomains, NamespaceUpdates}
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException, InvalidValueException}
import com.convergencelabs.convergence.server.model.domain.Namespace
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, AuthorizationProfileData, Permissions}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class NamespaceStoreActor private(context: ActorContext[NamespaceStoreActor.Message],
                                  namespaceStore: NamespaceStore,
                                  roleStore: RoleStore,
                                  configStore: ConfigStore)
  extends AbstractBehavior[NamespaceStoreActor.Message](context) {

  import NamespaceStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
      .flatMap(_ => namespaceStore.createNamespace(namespaceId, displayName, userNamespace = false))
      .map(_ => CreateNamespaceResponse(Right(Ok())))
      .recover {
        case DuplicateValueException(field, _, _) =>
          CreateNamespaceResponse(Left(NamespaceAlreadyExistsError(field)))
        case cause =>
          context.log.error("unexpected error creating namespace", cause)
          CreateNamespaceResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetNamespace(getRequest: GetNamespaceRequest): Unit = {
    val GetNamespaceRequest(namespaceId, replyTo) = getRequest
    namespaceStore.getNamespace(namespaceId)
      .map(_.map(ns => GetNamespaceResponse(Right(ns)))
        .getOrElse(GetNamespaceResponse(Left(NamespaceNotFoundError()))
        ))
      .recover { cause =>
        context.log.error("unexpected error getting namespace", cause)
        GetNamespaceResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateNamespace(request: UpdateNamespaceRequest): Unit = {
    val UpdateNamespaceRequest(_, namespaceId, displayName, replyTo) = request
    namespaceStore.updateNamespace(NamespaceUpdates(namespaceId, displayName))
      .map(_ => UpdateNamespaceResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateNamespaceResponse(Left(NamespaceNotFoundError()))
        case cause =>
          context.log.error("unexpected error updating namespace", cause)
          UpdateNamespaceResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onDeleteNamespace(deleteRequest: DeleteNamespaceRequest): Unit = {
    val DeleteNamespaceRequest(_, namespaceId, replyTo) = deleteRequest
    context.log.debug("Delete Namespace: " + namespaceId)
    (for {
      _ <- roleStore.removeAllRolesFromTarget(NamespaceRoleTarget(namespaceId))
      _ <- namespaceStore.deleteNamespace(namespaceId)
    } yield ())
      .map(_ => DeleteNamespaceResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteNamespaceResponse(Left(NamespaceNotFoundError()))
        case cause =>
          context.log.error("unexpected error updating namespace", cause)
          DeleteNamespaceResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetAccessibleNamespaces(getRequest: GetAccessibleNamespacesRequest): Unit = {
    val GetAccessibleNamespacesRequest(authProfileData, _, offset, limit, replyTo) = getRequest
    val authProfile = AuthorizationProfile(authProfileData)
    if (authProfile.hasGlobalPermission(Permissions.Server.ManageDomains)) {
      namespaceStore.getAllNamespacesAndDomains(offset, limit)
    } else {
      namespaceStore
        .getAccessibleNamespaces(authProfile.username)
        .flatMap(namespaces => namespaceStore.getNamespaceAndDomains(namespaces.map(_.id).toSet, offset, limit))
    }
      .map(n => GetAccessibleNamespacesResponse(Right(n)))
      .recover { cause =>
        context.log.error("unexpected error getting namespaces", cause)
        GetAccessibleNamespacesResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
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

  def apply(namespaceStore: NamespaceStore,
            roleStore: RoleStore,
            configStore: ConfigStore): Behavior[Message] =
    Behaviors.setup(context => new NamespaceStoreActor(context, namespaceStore, roleStore, configStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // CreateNamespace
  //
  final case class CreateNamespaceRequest(requester: String,
                                          namespaceId: String,
                                          displayName: String,
                                          replyTo: ActorRef[CreateNamespaceResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[NamespaceAlreadyExistsError], name = "already_exists")
  ))
  sealed trait CreateNamespaceError

  final case class NamespaceAlreadyExistsError(field: String) extends CreateNamespaceError

  final case class CreateNamespaceResponse(response: Either[CreateNamespaceError, Ok]) extends CborSerializable

  //
  // CreateNamespace
  //
  final case class UpdateNamespaceRequest(requester: String,
                                          namespaceId: String,
                                          displayName: String,
                                          replyTo: ActorRef[UpdateNamespaceResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[NamespaceNotFoundError], name = "not_found")
  ))
  sealed trait UpdateNamespaceError

  final case class UpdateNamespaceResponse(response: Either[UpdateNamespaceError, Ok]) extends CborSerializable

  //
  // CreateNamespace
  //
  final case class DeleteNamespaceRequest(requester: String, namespaceId: String, replyTo: ActorRef[DeleteNamespaceResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[NamespaceNotFoundError], name = "not_found")
  ))
  sealed trait DeleteNamespaceError

  final case class DeleteNamespaceResponse(response: Either[DeleteNamespaceError, Ok]) extends CborSerializable

  //
  // GetAccessibleNamespacesRequest
  //
  final case class GetAccessibleNamespacesRequest(requester: AuthorizationProfileData,
                                                  filter: Option[String],
                                                  @JsonDeserialize(contentAs = classOf[Long])
                                                  offset: QueryOffset,
                                                  @JsonDeserialize(contentAs = classOf[Long])
                                                  limit: QueryLimit,
                                                  replyTo: ActorRef[GetAccessibleNamespacesResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetAccessibleNamespacesError

  final case class GetAccessibleNamespacesResponse(namespaces: Either[GetAccessibleNamespacesError, Set[NamespaceAndDomains]])
    extends CborSerializable

  //
  // GetNamespace
  //
  final case class GetNamespaceRequest(namespaceId: String, replyTo: ActorRef[GetNamespaceResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[NamespaceNotFoundError], name = "not_found")
  ))
  sealed trait GetNamespaceError

  final case class GetNamespaceResponse(namespace: Either[GetNamespaceError, Namespace]) extends CborSerializable

  //
  // Common Errors
  //
  final case class NamespaceNotFoundError() extends AnyRef
    with UpdateNamespaceError
    with DeleteNamespaceError
    with GetNamespaceError

  final case class UnknownError() extends AnyRef
    with CreateNamespaceError
    with UpdateNamespaceError
    with DeleteNamespaceError
    with GetAccessibleNamespacesError
    with GetNamespaceError

}