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
import com.convergencelabs.convergence.server.db.DatabaseProvider
import grizzled.slf4j.Logging

import scala.language.postfixOps
import scala.util.{Failure, Success}

private class ConfigStoreActor private[datastore](private[this] val context: ActorContext[ConfigStoreActor.Message],
                                                  private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[ConfigStoreActor.Message](context) with Logging {

  import ConfigStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val configStore = new ConfigStore(dbProvider)

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: SetConfigsRequest =>
        onSetConfigs(msg)
      case msg: GetConfigsRequest =>
        onGetConfigs(msg)
      case msg: GetConfigsByFilterRequest =>
        onGetConfigsByFilter(msg)
    }

    Behaviors.same
  }

  private[this] def onSetConfigs(setConfigs: SetConfigsRequest): Unit = {
    val SetConfigsRequest(configs, replyTo) = setConfigs
    configStore.setConfigs(configs) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetConfigs(getConfigs: GetConfigsRequest): Unit = {
    val GetConfigsRequest(keys, replyTo) = getConfigs
    (keys match {
      case Some(k) =>
        configStore.getConfigs(k)
      case None =>
        configStore.getConfigs()
    }) match {
      case Success(configs) =>
        replyTo ! GetConfigsSuccess(configs)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetConfigsByFilter(getConfigs: GetConfigsByFilterRequest): Unit = {
    val GetConfigsByFilterRequest(filters, replyTo) = getConfigs
    configStore.getConfigsByFilter(filters) match {
      case Success(configs) =>
        replyTo ! GetConfigsByFilterSuccess(configs)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object ConfigStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("ConfigStore")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] = Behaviors.setup { context =>
    new ConfigStoreActor(context, dbProvider)
  }

  sealed trait Message extends CborSerializable

  //
  // SetConfigs
  //
  case class SetConfigsRequest(configs: Map[String, Any], actorRef: ActorRef[SetConfigsResponse]) extends Message

  sealed trait SetConfigsResponse extends CborSerializable

  //
  // GetConfigs
  //
  case class GetConfigsRequest(keys: Option[List[String]], actorRef: ActorRef[GetConfigsResponse]) extends Message

  sealed trait GetConfigsResponse extends CborSerializable

  case class GetConfigsSuccess(configs: Map[String, Any]) extends GetConfigsResponse

  //
  // GetConfigsByFilter
  //
  case class GetConfigsByFilterRequest(filters: List[String], actorRef: ActorRef[GetConfigsByFilterResponse]) extends Message

  sealed trait GetConfigsByFilterResponse extends CborSerializable

  case class GetConfigsByFilterSuccess(configs: Map[String, Any]) extends GetConfigsByFilterResponse

  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with SetConfigsResponse
    with GetConfigsResponse
    with GetConfigsByFilterResponse


  case class RequestSuccess() extends CborSerializable
    with SetConfigsResponse

}