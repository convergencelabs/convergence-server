/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.server.BuildInfo
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.db.DatabaseProvider

import scala.language.postfixOps
import scala.util.Try

object ServerStatusActor {
  val RelativePath = "ServerStatusActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new ServerStatusActor(dbProvider))

  case object GetStatusRequest
  case class ServerStatusResponse(version: String, distribution: String, status: String, namespaces: Long, domains: Long)
}

class ServerStatusActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import ServerStatusActor._

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val namespaceStore = new NamespaceStore(dbProvider)
  
  def receive: Receive = {
    case GetStatusRequest =>
      handleGetStatus()
    case message: Any =>
      unhandled(message)
  }

  def handleGetStatus(): Unit = {
    reply(for {
      domains <- domainStore.domainCount()
      namespaces <- namespaceStore.namespaceCount()
      distribution <- Try(this.context.system.settings.config.getString("convergence.distribution"))
    } yield {
      ServerStatusResponse(BuildInfo.version, distribution, "healthy", namespaces, domains)
    })
  }

}
