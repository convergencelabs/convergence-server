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

package com.convergencelabs.convergence.server.db.provision

import akka.actor.typed.pubsub.Topic.Publish
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.domain.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

class DomainProvisionerActor private(context: ActorContext[DomainProvisionerActor.Message],
                                     provisioner: DomainProvisioner,
                                     domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends AbstractBehavior[DomainProvisionerActor.Message](context) with Logging {

  private[this] implicit val ec: ExecutionContext = context.executionContext

  import DomainProvisionerActor._

  override def onMessage(msg: Message): Behavior[Message] = msg match {
    case provision: ProvisionDomain =>
      provisionDomain(provision)
    case destroy: DestroyDomain =>
      destroyDomain(destroy)
  }

  private[this] def provisionDomain(provision: ProvisionDomain): Behavior[Message] = {
    val ProvisionDomain(data, replyTo) = provision
    Future {
      provisioner.provisionDomain(data) map { _ =>
        replyTo ! ProvisionDomainResponse(Right(Ok()))
      } recover {
        case cause: Exception =>
          error(s"Error provisioning domain: ${data.domainId}", cause)
          replyTo ! ProvisionDomainResponse(Left(UnknownError()))
      }
    }
    Behaviors.same
  }

  private[this] def destroyDomain(destroy: DestroyDomain): Behavior[Message] = {
    val DestroyDomain(domainId, databaseUri, replyTo) = destroy
    Future {
      provisioner.destroyDomain(databaseUri) map { _ =>
        val message = DomainLifecycleTopic.DomainDeleted(domainId)
        replyTo ! DestroyDomainResponse(Right(Ok()))
        domainLifecycleTopic ! Publish(message)
      } recover { cause =>
        error(s"Error destroying domain: $domainId", cause)
        replyTo ! DestroyDomainResponse(Left(UnknownError()))
      }
    }

    Behaviors.same
  }
}

object DomainProvisionerActor {

  def apply(provisioner: DomainProvisioner,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup(context => new DomainProvisionerActor(context, provisioner, domainLifecycleTopic))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  final case class ProvisionDomain(data: ProvisionRequest, replyTo: ActorRef[ProvisionDomainResponse]) extends Message

  final case class ProvisionDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class DestroyDomain(domainFqn: DomainId, databaseUri: String, replyTo: ActorRef[DestroyDomainResponse]) extends Message

  final case class DestroyDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable
  
  final case class UnknownError()
}
