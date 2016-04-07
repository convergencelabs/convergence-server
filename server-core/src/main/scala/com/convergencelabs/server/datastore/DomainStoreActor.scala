package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.DomainStoreActor._
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import scala.util.Failure
import akka.actor.Props

class DomainStoreActor private[datastore] (private[this] val dbPool: OPartitionedDatabasePool)
    extends Actor with ActorLogging {

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("domain")

  private[this] val domainStore: DomainStore = new DomainStore(dbPool)

  private[this] val domainDBContoller: DomainDBController =
    if (domainConfig.getString("uri").startsWith("remote:")) {
      new DomainRemoteDBController(domainConfig)
    } else {
      new DomainMemoryDBController(domainConfig)
    }

  def receive: Receive = {
    case createRequest: CreateDomainRequest => sender ! createDomain(createRequest)
    case deleteRequest: DeleteDomainRequest => sender ! deleteDomain(deleteRequest)
    case getRequest: GetDomainRequest       => sender ! getDomain(getRequest)
    case listRequest: ListDomainsRequest    => sender ! listDomains(listRequest)
    case message: Any                       => unhandled(message)
  }

  def createDomain(createRequest: CreateDomainRequest): Try[Unit] = {
    val CreateDomainRequest(namespace, domainId, displayName, owner) = createRequest
    val DBConfig(id, username, password) = domainDBContoller.createDomain()
    //TODO: Need to handle rollback of domain creation if this fails
    domainStore.createDomain(Domain(id, DomainFqn(namespace, domainId), username, password, displayName, owner))

  }

  def deleteDomain(deleteRequest: DeleteDomainRequest): Try[Unit] = {
    val DeleteDomainRequest(namespace, domainId) = deleteRequest
    val domain = domainStore.getDomainByFqn(DomainFqn(namespace, domainId))
    domain flatMap {
      case Some(domain) => {
        domainStore.removeDomain(domain.id)
        domainDBContoller.deleteDomain(domain.id)
        Success(Unit)
      }
      //TODO: Determine correct exception to throw here
      case None => Failure(new IllegalArgumentException("Domain Not Found"))
    }
  }

  def getDomain(getRequest: GetDomainRequest): Try[GetDomainResponse] = {
    val GetDomainRequest(namespace, domainId) = getRequest
    domainStore.getDomainByFqn(DomainFqn(namespace, domainId)) map {
      case Some(domain) => GetDomainSuccess(domain)
      case None         => GetDomainFailure
    }
  }

  def listDomains(listRequest: ListDomainsRequest): Try[ListDomainsResponse] = {
    domainStore.getDomainsByOwner(listRequest.uid) map (ListDomainsResponse)
  }
}

object DomainStoreActor {
  def props(dbPool: OPartitionedDatabasePool): Props = Props(new DomainStoreActor(dbPool))

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, owner: String)

  case class DeleteDomainRequest(namespace: String, domainId: String)

  case class GetDomainRequest(namespace: String, domainId: String)

  sealed trait GetDomainResponse
  case class GetDomainSuccess(domain: Domain) extends GetDomainResponse
  case object GetDomainFailure extends GetDomainResponse

  case class ListDomainsRequest(uid: String)

  case class ListDomainsResponse(domains: List[Domain])
}

