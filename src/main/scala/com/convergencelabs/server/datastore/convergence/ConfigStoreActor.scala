/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import scala.language.postfixOps

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.db.DatabaseProvider

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
