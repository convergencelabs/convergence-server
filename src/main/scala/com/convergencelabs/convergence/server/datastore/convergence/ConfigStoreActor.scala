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

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.db.DatabaseProvider

import scala.language.postfixOps

class ConfigStoreActor private[datastore](
                                           private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import ConfigStoreActor._

  private[this] val configStore = new ConfigStore(dbProvider)

  def receive: Receive = {
    case msg: SetConfigsRequest =>
      onSetConfigs(msg)
    case msg: GetConfigsRequest =>
      onGetConfigs(msg)
    case msg: GetConfigsByFilterRequest =>
      onGetConfigsByFilter(msg)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onSetConfigs(setConfigs: SetConfigsRequest): Unit = {
    val SetConfigsRequest(configs) = setConfigs
    reply(configStore.setConfigs(configs))
  }

  private[this] def onGetConfigs(getConfigs: GetConfigsRequest): Unit = {
    val GetConfigsRequest(keys) = getConfigs
    reply((keys match {
      case Some(k) => configStore.getConfigs(k)
      case None => configStore.getConfigs()
    }).map(GetConfigsResponse))
  }

  private[this] def onGetConfigsByFilter(getConfigs: GetConfigsByFilterRequest): Unit = {
    val GetConfigsByFilterRequest(filters) = getConfigs
    reply(configStore.getConfigsByFilter(filters).map(GetConfigsByFilterResponse))
  }
}


object ConfigStoreActor {
  val RelativePath = "ConfigStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new ConfigStoreActor(dbProvider))

  sealed trait ConfigStoreActorRequest extends CborSerializable

  case class SetConfigsRequest(configs: Map[String, Any]) extends ConfigStoreActorRequest

  case class GetConfigsRequest(keys: Option[List[String]]) extends ConfigStoreActorRequest
  case class GetConfigsResponse(configs: Map[String, Any]) extends CborSerializable

  case class GetConfigsByFilterRequest(filters: List[String]) extends ConfigStoreActorRequest
  case class GetConfigsByFilterResponse(configs: Map[String, Any]) extends CborSerializable

}