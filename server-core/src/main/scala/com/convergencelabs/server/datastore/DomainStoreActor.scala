package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Try
import java.util.UUID

class DomainStoreActor private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool)
    extends StoreActor with ActorLogging {

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val domainStore: DomainStore = new DomainStore(dbPool)

  private[this] val domainDBContoller: DomainDBController =
    if (domainConfig.getString("uri").startsWith("remote:")) {
      new DomainRemoteDBController(domainConfig)
    } else {
      new DomainMemoryDBController(domainConfig)
    }

  def receive: Receive = {
    case createRequest: CreateDomainRequest => createDomain(createRequest)
    case deleteRequest: DeleteDomainRequest => deleteDomain(deleteRequest)
    case getRequest: GetDomainRequest       => getDomain(getRequest)
    case listRequest: ListDomainsRequest    => listDomains(listRequest)
    case message: Any                       => unhandled(message)
  }

  def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, owner) = createRequest
    val DBConfig(dbName, username, password) = domainDBContoller.createDomain()
    // FIXME: Determine correct way to create id
    val id = UUID.randomUUID().toString()
    // TODO: Need to handle rollback of domain creation if this fails
    reply(domainStore.createDomain(Domain(id, DomainFqn(namespace, domainId), displayName, owner), dbName, username, password))
  }

  def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId) = deleteRequest
    val domainFqn = DomainFqn(namespace, domainId)
    val domain = domainStore.getDomainByFqn(domainFqn)
    val databaseConfig = domainStore.getDomainDatabaseInfo(domainFqn)
    reply((domain, databaseConfig) match {
      case (Success(Some(domain)), Success(Some(databaseConfig))) => {
        domainStore.removeDomain(domain.id)
        domainDBContoller.deleteDomain(databaseConfig.database)
        Success(DeleteSuccess)
      }
      case _ => Success(NotFound)
    })
  }

  def getDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId) = getRequest
    reply(domainStore.getDomainByFqn(DomainFqn(namespace, domainId)))
  }

  def listDomains(listRequest: ListDomainsRequest): Unit = {
    reply(domainStore.getDomainsByOwner(listRequest.uid))
  }
}

object DomainStoreActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new DomainStoreActor(dbPool))

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, owner: String)
  case class DeleteDomainRequest(namespace: String, domainId: String)
  case class GetDomainRequest(namespace: String, domainId: String)
  case class ListDomainsRequest(uid: String)
}
