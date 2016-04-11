package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DomainNotFound
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainSuccess
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsResponse
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.Props

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
    case getRequest: GetDomainRequest => getDomain(getRequest)
    case listRequest: ListDomainsRequest => listDomains(listRequest)
    case message: Any => unhandled(message)
  }

  def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, owner) = createRequest
    val DBConfig(id, username, password) = domainDBContoller.createDomain()
    // TODO: Need to handle rollback of domain creation if this fails
    reply(domainStore.createDomain(Domain(id, DomainFqn(namespace, domainId), displayName, owner), username, password))
  }

  def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId) = deleteRequest
    val domain = domainStore.getDomainByFqn(DomainFqn(namespace, domainId))
    reply(domain flatMap {
      case Some(domain) =>
        domainStore.removeDomain(domain.id)
        domainDBContoller.deleteDomain(domain.id)
        Success(Unit)
      case None =>
        // TODO: Determine correct exception to throw here
        Failure(new IllegalArgumentException("Domain Not Found"))
    })
  }

  def getDomain(getRequest: GetDomainRequest): Unit = {
    val GetDomainRequest(namespace, domainId) = getRequest
    mapAndReply(domainStore.getDomainByFqn(DomainFqn(namespace, domainId))) {
      case Some(domain) => GetDomainSuccess(domain)
      case None => DomainNotFound
    }
  }

  def listDomains(listRequest: ListDomainsRequest): Unit = {
    mapAndReply(domainStore.getDomainsByOwner(listRequest.uid))(ListDomainsResponse(_))
  }
}

object DomainStoreActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new DomainStoreActor(dbPool))

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, owner: String)

  case class DeleteDomainRequest(namespace: String, domainId: String)

  case class GetDomainRequest(namespace: String, domainId: String)

  sealed trait GetDomainResponse
  case class GetDomainSuccess(domain: Domain) extends GetDomainResponse
  case object DomainNotFound extends GetDomainResponse

  case class ListDomainsRequest(uid: String)

  case class ListDomainsResponse(domains: List[Domain])
}
