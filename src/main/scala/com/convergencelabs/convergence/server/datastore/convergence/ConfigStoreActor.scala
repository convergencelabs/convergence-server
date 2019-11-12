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

import scala.language.postfixOps

import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.db.DatabaseProvider

import akka.actor.ActorLogging
import akka.actor.Props

object ConfigStoreActor {
  val RelativePath = "ConfigStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new ConfigStoreActor(dbProvider))

  case class SetConfigs(configs: Map[String, Any])
  case class GetConfigs(keys: Option[List[String]])
  case class GetConfigsByFilter(filters: List[String])
}

class ConfigStoreActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import ConfigStoreActor._

  private[this] val configStore = new ConfigStore(dbProvider)

  def receive: Receive = {
    case msg: SetConfigs =>
      setConfigs(msg)
    case msg: GetConfigs =>
      getConfigs(msg)
    case msg: GetConfigsByFilter =>
      getConfigsByFilter(msg)
    case message: Any =>
      unhandled(message)
  }

  def setConfigs(setConfigs: SetConfigs): Unit = {
    val SetConfigs(configs) = setConfigs
    reply(configStore.setConfigs(configs))
  }

  def getConfigs(getConfigs: GetConfigs): Unit = {
    val GetConfigs(keys) = getConfigs
    keys match {
      case Some(k) => reply(configStore.getConfigs(k))
      case None => reply(configStore.getConfigs())
    }
  }
  
  def getConfigsByFilter(getConfigs: GetConfigsByFilter): Unit = {
    val GetConfigsByFilter(filters) = getConfigs
    reply(configStore.getConfigsByFilter(filters))
  }
}
