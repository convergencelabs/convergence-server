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

package com.convergencelabs.convergence.server.backend.services.domain.rest

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.ConvergenceJwtUtil
import com.convergencelabs.convergence.server.backend.datastore.domain.config.DomainConfigStore
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserDeletionOrchestrator
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.collection.CollectionStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.config.ConfigStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.group.UserGroupStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.jwt.JwtAuthKeyStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelPermissionsStoreActor, ModelServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.Message
import com.convergencelabs.convergence.server.backend.services.domain.session.SessionStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.stats.DomainStatsActor
import com.convergencelabs.convergence.server.backend.services.domain.user.DomainUserStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.{AuthenticationHandler, BaseDomainShardedActor, DomainPersistenceManager, activity}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

private final class DomainRestActor(domainId: DomainId,
                                    context: ActorContext[DomainRestActor.Message],
                                    shardRegion: ActorRef[Message],
                                    shard: ActorRef[ClusterSharding.ShardCommand],
                                    domainPersistenceManager: DomainPersistenceManager,
                                    receiveTimeout: FiniteDuration,
                                    modelServiceActor: ActorRef[ModelServiceActor.Message],
                                    chatServiceActor: ActorRef[ChatServiceActor.Message],
                                    activityServiceActor: ActorRef[activity.ActivityServiceActor.Message],
                                   )
  extends BaseDomainShardedActor[DomainRestActor.Message](
    domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import DomainRestActor._

  private[this] var userStoreActor: ActorRef[DomainUserStoreActor.Message] = _
  private[this] var statsActor: ActorRef[DomainStatsActor.Message] = _
  private[this] var collectionStoreActor: ActorRef[CollectionStoreActor.Message] = _
  private[this] var modelPermissionsStoreActor: ActorRef[ModelPermissionsStoreActor.Message] = _
  private[this] var keyStoreActor: ActorRef[JwtAuthKeyStoreActor.Message] = _
  private[this] var sessionStoreActor: ActorRef[SessionStoreActor.Message] = _
  private[this] var configStoreActor: ActorRef[ConfigStoreActor.Message] = _
  private[this] var groupStoreActor: ActorRef[UserGroupStoreActor.Message] = _
  private[this] var domainConfigStore: DomainConfigStore = _

  override def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {

      case DomainRestMessage(_, body) =>
        body match {
          case DomainRestMessageBody.Domain(message) =>
            onDomainMessage(message)
          case DomainRestMessageBody.Model(message) =>
            modelServiceActor ! message
          case DomainRestMessageBody.ModelPermission(message) =>
            modelPermissionsStoreActor ! message
          case DomainRestMessageBody.User(message) =>
            userStoreActor ! message
          case DomainRestMessageBody.Group(message) =>
            groupStoreActor ! message
          case DomainRestMessageBody.Collection(message) =>
            collectionStoreActor ! message
          case DomainRestMessageBody.JwtAuthKey(message) =>
            keyStoreActor ! message
          case DomainRestMessageBody.Config(message) =>
            configStoreActor ! message
          case DomainRestMessageBody.Stats(message) =>
            statsActor ! message
          case DomainRestMessageBody.Session(message) =>
            sessionStoreActor ! message
          case DomainRestMessageBody.Activity(message) =>
            activityServiceActor ! message
          case DomainRestMessageBody.Chat(message) =>
            chatServiceActor ! message
          case _ =>
            logger.warn(s"Unexpected DomainRestMessageBody: $body")
        }
        Behaviors.same

      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  private[this] def onDomainMessage(message: DomainMessage): Unit = {
    message match {
      case msg: AdminTokenRequest =>
        onGetAdminToken(msg)
    }
  }

  private[this] def onGetAdminToken(msg: AdminTokenRequest): Unit = {
    val AdminTokenRequest(convergenceUsername, replyTo) = msg
    domainConfigStore
      .getAdminKeyPair()
      .flatMap(pair => ConvergenceJwtUtil.fromString(AuthenticationHandler.AdminKeyId, pair.privateKey))
      .flatMap(util => util.generateToken(convergenceUsername))
      .map(token => AdminTokenResponse(Right(token)))
      .recover { cause =>
        context.log.error("Unexpected error getting admin token.", cause)
        AdminTokenResponse(Left(()))
      }
      .foreach(replyTo ! _)
  }

  override protected def initializeState(msg: Message): Try[Unit] = {
    domainConfigStore = persistenceProvider.configStore
    statsActor = context.spawn(DomainStatsActor(persistenceProvider), "DomainStats")
    val userDeleter = new DomainUserDeletionOrchestrator(
      persistenceProvider.userStore,
      persistenceProvider.userGroupStore,
      persistenceProvider.chatStore,
      persistenceProvider.permissionsStore,
      persistenceProvider.modelPermissionsStore,
      persistenceProvider.collectionPermissionsStore)

    userStoreActor = context.spawn(DomainUserStoreActor(
      persistenceProvider.userStore, userDeleter), "UserStore")
    configStoreActor = context.spawn(ConfigStoreActor(
      persistenceProvider.configStore), "ConfigStore")
    collectionStoreActor = context.spawn(CollectionStoreActor(
      persistenceProvider.collectionStore, persistenceProvider.collectionPermissionsStore), "CollectionStore")
    modelPermissionsStoreActor = context.spawn(ModelPermissionsStoreActor(
      persistenceProvider.modelPermissionsStore), "ModelPermissionsStore")
    keyStoreActor = context.spawn(JwtAuthKeyStoreActor(
      persistenceProvider.jwtAuthKeyStore), "JwtAuthKeyStore")
    sessionStoreActor = context.spawn(SessionStoreActor(
      persistenceProvider.sessionStore), "SessionStore")
    groupStoreActor = context.spawn(UserGroupStoreActor(
      persistenceProvider.userGroupStore, persistenceProvider.permissionsStore), "GroupStore")

    Success(())
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(domainId)
}

object DomainRestActor {
  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration,
            modelServiceActor: ActorRef[ModelServiceActor.Message],
            chatServiceActor: ActorRef[ChatServiceActor.Message],
            activityServiceActor: ActorRef[activity.ActivityServiceActor.Message],
           ): Behavior[Message] = Behaviors.setup(context =>
    new DomainRestActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout,
      modelServiceActor,
      chatServiceActor,
      activityServiceActor)
  )

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    def domainId: DomainId
  }

  final object DomainRestMessage {

    def apply(domainId: DomainId, msg: DomainMessage): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Domain(msg))
    }

    def apply(domainId: DomainId, msg: ModelServiceActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Model(msg))
    }

    def apply(domainId: DomainId, msg: ModelPermissionsStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.ModelPermission(msg))
    }

    def apply(domainId: DomainId, msg: ChatServiceActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Chat(msg))
    }

    def apply(domainId: DomainId, msg: ActivityServiceActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Activity(msg))
    }

    def apply(domainId: DomainId, msg: DomainUserStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.User(msg))
    }

    def apply(domainId: DomainId, msg: UserGroupStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Group(msg))
    }

    def apply(domainId: DomainId, msg: CollectionStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Collection(msg))
    }

    def apply(domainId: DomainId, msg: SessionStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Session(msg))
    }

    def apply(domainId: DomainId, msg: DomainStatsActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Stats(msg))
    }

    def apply(domainId: DomainId, msg: JwtAuthKeyStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.JwtAuthKey(msg))
    }

    def apply(domainId: DomainId, msg: ConfigStoreActor.Message): DomainRestMessage = {
      DomainRestMessage(domainId, DomainRestMessageBody.Config(msg))
    }
  }

  final case class DomainRestMessage(domainId: DomainId, message: DomainRestMessageBody) extends Message


  //
  // AdminToken
  //

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AdminTokenRequest], name = "admin_token")
  ))
  sealed trait DomainMessage

  final case class AdminTokenRequest(convergenceUsername: String, replyTo: ActorRef[AdminTokenResponse]) extends DomainMessage

  final case class AdminTokenResponse(token: Either[Unit, String]) extends CborSerializable

  private case class ReceiveTimeout(domainId: DomainId) extends Message

}
