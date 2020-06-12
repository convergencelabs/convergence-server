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

package com.convergencelabs.convergence.server.domain.rest

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.ConvergenceJwtUtil
import com.convergencelabs.convergence.server.actor.{CborSerializable, ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.datastore.domain
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor
import com.convergencelabs.convergence.server.domain.{AuthenticationHandler, DomainId}
import com.fasterxml.jackson.annotation.JsonTypeInfo

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class DomainRestActor(context: ActorContext[DomainRestActor.Message],
                      shardRegion: ActorRef[DomainRestActor.Message],
                      shard: ActorRef[ClusterSharding.ShardCommand],
                      domainPersistenceManager: DomainPersistenceManager, receiveTimeout: FiniteDuration)
  extends ShardedActor[DomainRestActor.Message](context, shardRegion, shard) {

  import DomainRestActor._

  private[this] var domainFqn: DomainId = _
  private[this] var userStoreActor: ActorRef[UserStoreActor.Message] = _
  private[this] var statsActor: ActorRef[DomainStatsActor.Message] = _
  private[this] var collectionStoreActor: ActorRef[CollectionStoreActor.Message] = _
  private[this] var modelStoreActor: ActorRef[ModelStoreActor.Message] = _
  private[this] var modelPermissionsStoreActor: ActorRef[ModelPermissionsStoreActor.Message] = _
  private[this] var keyStoreActor: ActorRef[JwtAuthKeyStoreActor.Message] = _
  private[this] var sessionStoreActor: ActorRef[SessionStoreActor.Message] = _
  private[this] var configStoreActor: ActorRef[ConfigStoreActor.Message] = _
  private[this] var groupStoreActor: ActorRef[UserGroupStoreActor.Message] = _
  private[this] var chatActor: ActorRef[ChatManagerActor.Message] = _
  private[this] var domainConfigStore: DomainConfigStore = _

  override def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case DomainRestMessage(_, body) =>
        body match {
          case msg: AdminTokenRequest =>
            onGetAdminToken(msg)
          case message: UserStoreActor.Message =>
            userStoreActor ! message
          case message: UserGroupStoreActor.Message =>
            groupStoreActor ! message
          case message: CollectionStoreActor.Message =>
            collectionStoreActor ! message
          case message: ModelStoreActor.Message =>
            modelStoreActor ! message
          case message: ModelPermissionsStoreActor.Message =>
            modelPermissionsStoreActor ! message
          case message: JwtAuthKeyStoreActor.Message =>
            keyStoreActor ! message
          case message: ConfigStoreActor.Message =>
            configStoreActor ! message
          case message: DomainStatsActor.Message =>
            statsActor ! message
          case message: domain.SessionStoreActor.Message =>
            sessionStoreActor ! message
          case message: ChatManagerActor.Message =>
            chatActor ! message
        }

        Behaviors.same

      case ReceiveTimeout(_) =>
        this.passivate()
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

  override protected def initialize(msg: Message): Try[ShardedActorStatUpPlan] = {
    this.context.setReceiveTimeout(this.receiveTimeout, ReceiveTimeout(msg.domainId))

    domainPersistenceManager.acquirePersistenceProvider(context.self, context.system, msg.domainId) map { provider =>
      domainConfigStore = provider.configStore
      statsActor = context.spawn(DomainStatsActor(provider), "DomainStats")
      userStoreActor = context.spawn(UserStoreActor(provider.userStore), "UserStore")
      configStoreActor = context.spawn(ConfigStoreActor(provider.configStore), "ConfigStore")
      collectionStoreActor = context.spawn(CollectionStoreActor(provider.collectionStore), "CollectionStore")
      modelStoreActor = context.spawn(ModelStoreActor(provider), "ModelStore")
      modelPermissionsStoreActor = context.spawn(ModelPermissionsStoreActor(provider.modelPermissionsStore), "ModelPermissionsStore")
      keyStoreActor = context.spawn(JwtAuthKeyStoreActor(provider.jwtAuthKeyStore), "JwtAuthKeyStore")
      sessionStoreActor = context.spawn(SessionStoreActor(provider.sessionStore), "SessionStore")
      groupStoreActor = context.spawn(UserGroupStoreActor(provider.userGroupStore), "GroupStore")
      chatActor = context.spawn(ChatManagerActor(provider.chatStore, provider.permissionsStore), "ChatManager")

      StartUpRequired
    } recoverWith {
      case NonFatal(cause) =>
        Failure(cause)
    }
  }

  override protected def passivate(): Behavior[Message] = {
    Option(this.domainFqn).foreach(d =>
      domainPersistenceManager.releasePersistenceProvider(context.self, context.system, d)
    )

    super.passivate()
  }

  override protected def setIdentityData(message: Message): Try[String] = {
    this.domainFqn = message.domainId
    Success(s"${message.domainId.namespace}/${message.domainId.domainId}")
  }
}

object DomainRestActor {
  def apply(shardRegion: ActorRef[DomainRestActor.Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup(context =>
    new DomainRestActor(context, shardRegion, shard, domainPersistenceManager, receiveTimeout)
  )

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    def domainId: DomainId
  }

  private case class ReceiveTimeout(domainId: DomainId) extends Message

  case class DomainRestMessage(domainId: DomainId, message: Any) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
  trait DomainRestMessageBody

  //
  // AdminToken
  //
  case class AdminTokenRequest(convergenceUsername: String, replyTo: ActorRef[AdminTokenResponse]) extends DomainRestMessageBody

  case class AdminTokenResponse(token: Either[Unit, String]) extends CborSerializable

}
