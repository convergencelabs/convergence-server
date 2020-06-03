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
import com.convergencelabs.convergence.server.BuildInfo
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.db.DatabaseProvider
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

class ServerStatusActor private[datastore](private[this] val context: ActorContext[ServerStatusActor.Message],
                                            private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[ServerStatusActor.Message](context) with Logging {

  import ServerStatusActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val namespaceStore = new NamespaceStore(dbProvider)

  override def onMessage(msg: Message): Behavior[Message] = {
   msg match {
     case msg: GetStatusRequest =>
       onGetStatus(msg)
   }
    Behaviors.same
  }

  private[this] def onGetStatus(msg: GetStatusRequest): Unit = {
    val GetStatusRequest(replyTo) = msg
    (for {
      domains <- domainStore.domainCount()
      namespaces <- namespaceStore.namespaceCount()
      distribution <- Try(this.context.system.settings.config.getString("convergence.distribution"))
    } yield {
      ServerStatusResponse(BuildInfo.version, distribution, "healthy", namespaces, domains)
    }) match {
      case Success(status) =>
        replyTo ! GetStatusSuccess(status)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object ServerStatusActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("ServerStatusActor")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] =
    Behaviors.setup(context => new ServerStatusActor(context, dbProvider))

  case class ServerStatusResponse(version: String, distribution: String, status: String, namespaces: Long, domains: Long) extends CborSerializable

  sealed trait Message extends CborSerializable

  //
  // GetStatus
  //
  case class GetStatusRequest(replyTo: ActorRef[GetStatusResponse]) extends Message

  sealed trait GetStatusResponse extends CborSerializable

  case class GetStatusSuccess(status: ServerStatusResponse) extends GetStatusResponse

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetStatusResponse
}
