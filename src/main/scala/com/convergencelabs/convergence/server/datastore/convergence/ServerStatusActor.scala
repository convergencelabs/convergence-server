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
import com.convergencelabs.convergence.server.BuildInfo
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.db.DatabaseProvider

import scala.language.postfixOps
import scala.util.Try

class ServerStatusActor private[datastore](
                                            private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import ServerStatusActor._

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val namespaceStore = new NamespaceStore(dbProvider)

  def receive: Receive = {
    case GetStatusRequest =>
      onGetStatus()
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetStatus(): Unit = {
    reply(for {
      domains <- domainStore.domainCount()
      namespaces <- namespaceStore.namespaceCount()
      distribution <- Try(this.context.system.settings.config.getString("convergence.distribution"))
    } yield {
      ServerStatusResponse(BuildInfo.version, distribution, "healthy", namespaces, domains)
    })
  }

}


object ServerStatusActor {
  val RelativePath = "ServerStatusActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new ServerStatusActor(dbProvider))

  case object GetStatusRequest extends CborSerializable

  case class ServerStatusResponse(version: String, distribution: String, status: String, namespaces: Long, domains: Long) extends CborSerializable

}
