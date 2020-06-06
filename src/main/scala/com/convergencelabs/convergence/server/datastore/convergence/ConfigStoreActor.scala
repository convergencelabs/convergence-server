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
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.language.postfixOps

/**
 * The [ConfigStoreActor] handles requests for getting and setting Convergence
 * wide server configurations.
 *
 * @param context     The ActorContext for this actor.
 * @param configStore The configuration store for getting and setting configs.
 */
class ConfigStoreActor private(context: ActorContext[ConfigStoreActor.Message],
                               configStore: ConfigStore)
  extends AbstractBehavior[ConfigStoreActor.Message](context) {

  import ConfigStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
    configStore
      .setConfigs(configs)
      .map(_ => SetConfigsResponse(Right(())))
      .recover { cause =>
        context.log.error("Unexpected exception setting configs", cause)
        SetConfigsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetConfigs(getConfigs: GetConfigsRequest): Unit = {
    val GetConfigsRequest(keys, replyTo) = getConfigs
    keys
      .map(configStore.getConfigs)
      .getOrElse(configStore.getConfigs())
      .map(configs => GetConfigsResponse(Right(configs)))
      .recover { cause =>
        context.log.error("Unexpected exception getting configs", cause)
        GetConfigsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetConfigsByFilter(getConfigs: GetConfigsByFilterRequest): Unit = {
    val GetConfigsByFilterRequest(filters, replyTo) = getConfigs
    configStore
      .getConfigsByFilter(filters)
      .map(configs => GetConfigsByFilterResponse(Right(configs)))
      .recover { cause =>
        context.log.error("Unexpected exception getting configs by filter", cause)
        GetConfigsByFilterResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object ConfigStoreActor {

  val Key: ServiceKey[Message] = ServiceKey[Message]("ConfigStore")

  def apply(configStore: ConfigStore): Behavior[Message] = Behaviors.setup { context =>
    new ConfigStoreActor(context, configStore)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////
  sealed trait Message extends CborSerializable

  //
  // SetConfigs
  //
  case class SetConfigsRequest(configs: Map[String, Any], actorRef: ActorRef[SetConfigsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetConfigsError

  case class SetConfigsResponse(response: Either[SetConfigsError, Unit]) extends CborSerializable

  //
  // GetConfigs
  //
  case class GetConfigsRequest(keys: Option[List[String]], actorRef: ActorRef[GetConfigsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetConfigsError

  case class GetConfigsResponse(configs: Either[GetConfigsError, Map[String, Any]]) extends CborSerializable

  //
  // GetConfigsByFilter
  //
  case class GetConfigsByFilterRequest(filters: List[String], actorRef: ActorRef[GetConfigsByFilterResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetConfigsByFilterError

  case class GetConfigsByFilterResponse(configs: Either[GetConfigsByFilterError, Map[String, Any]]) extends CborSerializable

  //
  // Commons Errors
  //
  case class UnknownError() extends AnyRef
    with SetConfigsError
    with GetConfigsError
    with GetConfigsByFilterError

}