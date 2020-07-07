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

package com.convergencelabs.convergence.server.backend.services.server

import akka.actor.typed.pubsub.Topic.Publish
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager.DomainDatabaseCreationData
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

final class DomainDatabaseManagerActor private(context: ActorContext[DomainDatabaseManagerActor.Message],
                                               domainDatabaseManager: DomainDatabaseManager,
                                               domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends AbstractBehavior[DomainDatabaseManagerActor.Message](context) with Logging {

  private[this] implicit val ec: ExecutionContext = context.executionContext

  import DomainDatabaseManagerActor._

  override def onMessage(msg: Message): Behavior[Message] = msg match {
    case message: CreateDomainDatabaseRequest =>
      createDomainDatabase(message)
    case message: DestroyDomainRequest =>
      destroyDomain(message)
  }

  private[this] def createDomainDatabase(provision: CreateDomainDatabaseRequest): Behavior[Message] = {
    val CreateDomainDatabaseRequest(data, replyTo) = provision
    Future {
      domainDatabaseManager.createDomainDatabase(data) map { _ =>
        replyTo ! CreateDomainDatabaseResponse(Right(Ok()))
      } recover {
        case cause: Exception =>
          error(s"Error provisioning domain: ${data.domainId}", cause)
          replyTo ! CreateDomainDatabaseResponse(Left(UnknownError(Some(cause.getMessage))))
      }
    }
    Behaviors.same
  }

  private[this] def destroyDomain(destroy: DestroyDomainRequest): Behavior[Message] = {
    val DestroyDomainRequest(domainId, databaseUri, replyTo) = destroy
    Future {
      domainDatabaseManager.destroyDomain(databaseUri) map { _ =>
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

object DomainDatabaseManagerActor {

  def apply(databaseManager: DomainDatabaseManager,
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[Message] =
    Behaviors.setup(context => new DomainDatabaseManagerActor(context, databaseManager, domainLifecycleTopic))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  final case class CreateDomainDatabaseRequest(data: DomainDatabaseCreationData, replyTo: ActorRef[CreateDomainDatabaseResponse]) extends Message

  final case class CreateDomainDatabaseResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class DestroyDomainRequest(domainFqn: DomainId, databaseUri: String, replyTo: ActorRef[DestroyDomainResponse]) extends Message

  final case class DestroyDomainResponse(response: Either[UnknownError, Ok]) extends CborSerializable

  final case class UnknownError(message: Option[String] = None)

}
