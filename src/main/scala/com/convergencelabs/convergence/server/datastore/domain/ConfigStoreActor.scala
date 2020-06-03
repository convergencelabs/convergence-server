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

package com.convergencelabs.convergence.server.datastore.domain

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}


class ConfigStoreActor private[datastore](private[this] val context: ActorContext[ConfigStoreActor.Message],
                                          private[this] val store: DomainConfigStore)
  extends AbstractBehavior[ConfigStoreActor.Message](context) with Logging {

  import ConfigStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetAnonymousAuthRequest =>
        onGetAnonymousAuthEnabled(msg)
      case msg: SetAnonymousAuthRequest =>
        onSetAnonymousAuthEnabled(msg)
      case msg: GetModelSnapshotPolicyRequest =>
        onGetModelSnapshotPolicy(msg)
      case msg: SetModelSnapshotPolicyRequest =>
        onSetModelSnapshotPolicy(msg)
    }

    Behaviors.same
  }


  private[this] def onGetAnonymousAuthEnabled(msg: GetAnonymousAuthRequest): Unit = {
    val GetAnonymousAuthRequest(replyTo) = msg
    store.isAnonymousAuthEnabled() match {
      case Success(enabled) =>
        replyTo ! GetAnonymousAuthSuccess(enabled)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onSetAnonymousAuthEnabled(msg: SetAnonymousAuthRequest): Unit = {
    val SetAnonymousAuthRequest(enabled, replyTo) = msg
    store.setAnonymousAuthEnabled(enabled) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onGetModelSnapshotPolicy(msg: GetModelSnapshotPolicyRequest): Unit = {
    val GetModelSnapshotPolicyRequest(replyTo) = msg
    store.getModelSnapshotConfig() match {
      case Success(policy) =>
        replyTo ! GetModelSnapshotPolicySuccess(policy)
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }

  private[this] def onSetModelSnapshotPolicy(msg: SetModelSnapshotPolicyRequest): Unit = {
    val SetModelSnapshotPolicyRequest(policy, replyTo) = msg
    store.setModelSnapshotConfig(policy) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(exception) =>
        replyTo ! RequestFailure(exception)
    }
  }
}


object ConfigStoreActor {
  def apply(store: DomainConfigStore): Behavior[Message] = Behaviors.setup { context =>
    new ConfigStoreActor(context, store)
  }

  sealed trait Message extends CborSerializable with DomainRestMessageBody


  case class GetAnonymousAuthRequest(replyTo: ActorRef[GetAnonymousAuthResponse]) extends Message

  sealed trait GetAnonymousAuthResponse extends CborSerializable

  case class GetAnonymousAuthSuccess(enabled: Boolean) extends GetAnonymousAuthResponse


  case class SetAnonymousAuthRequest(enabled: Boolean, replyTo: ActorRef[SetAnonymousAuthResponse]) extends Message

  sealed trait SetAnonymousAuthResponse extends CborSerializable


  case class GetModelSnapshotPolicyRequest(replyTo: ActorRef[GetModelSnapshotPolicyResponse]) extends Message

  sealed trait GetModelSnapshotPolicyResponse extends CborSerializable

  case class GetModelSnapshotPolicySuccess(policy: ModelSnapshotConfig) extends GetModelSnapshotPolicyResponse


  case class SetModelSnapshotPolicyRequest(policy: ModelSnapshotConfig, replyTo: ActorRef[SetModelSnapshotPolicyResponse]) extends Message

  sealed trait SetModelSnapshotPolicyResponse extends CborSerializable


  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetAnonymousAuthResponse
    with SetAnonymousAuthResponse
    with GetModelSnapshotPolicyResponse
    with SetModelSnapshotPolicyResponse


  case class RequestSuccess() extends CborSerializable
    with SetModelSnapshotPolicyResponse
    with SetAnonymousAuthResponse

}