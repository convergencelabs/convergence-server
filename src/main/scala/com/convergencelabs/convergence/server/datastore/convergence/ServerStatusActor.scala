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
import com.fasterxml.jackson.annotation.JsonSubTypes

import scala.util.Try

class ServerStatusActor private(context: ActorContext[ServerStatusActor.Message],
                                domainStore: DomainStore,
                                namespaceStore: NamespaceStore)
  extends AbstractBehavior[ServerStatusActor.Message](context) {

  import ServerStatusActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
    })
      .map(s => GetStatusResponse(Right(s)))
      .recover { cause =>
        context.log.error("Unexpected error getting server status", cause)
        GetStatusResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}


object ServerStatusActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("ServerStatusActor")

  def apply(domainStore: DomainStore,
            namespaceStore: NamespaceStore): Behavior[Message] =
    Behaviors.setup(context => new ServerStatusActor(context, domainStore, namespaceStore))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // GetStatus
  //
  case class GetStatusRequest(replyTo: ActorRef[GetStatusResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetStatusError

  case class UnknownError() extends GetStatusError

  case class ServerStatusResponse(version: String, distribution: String, status: String, namespaces: Long, domains: Long) extends CborSerializable

  case class GetStatusResponse(status: Either[GetStatusError, ServerStatusResponse]) extends CborSerializable

}
