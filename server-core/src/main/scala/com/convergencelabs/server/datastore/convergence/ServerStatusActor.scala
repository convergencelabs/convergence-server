package com.convergencelabs.server.datastore.convergence

import scala.language.postfixOps

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.db.DatabaseProvider

import akka.actor.ActorLogging
import akka.actor.Props
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
      getStatus()
    case message: Any =>
      unhandled(message)
  }

  def getStatus(): Unit = {
    
    reply(for {
      domains <- domainStore.domainCount()
      namespaces <- namespaceStore.namespaceCount()
      version <- Try(this.context.system.settings.config.getString("convergence.version"))
      distribution <- Try(this.context.system.settings.config.getString("convergence.distribution"))
    } yield {
      ServerStatusResponse(version, distribution, "healthy", namespaces, domains)
    })
  }

}
