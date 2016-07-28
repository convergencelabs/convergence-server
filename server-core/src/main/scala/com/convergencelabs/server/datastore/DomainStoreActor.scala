package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.UpdateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Try
import scala.concurrent.ExecutionContext
import com.convergencelabs.server.domain.DomainStatus
import java.util.UUID
import com.convergencelabs.server.domain.DomainDatabaseInfo

class DomainStoreActor private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] implicit val ec: ExecutionContext)
    extends StoreActor with ActorLogging {

  private[this] val orientDbConfig: Config = context.system.settings.config.getConfig("convergence.orient-db")
  private[this] val domainDbConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val Username = domainDbConfig.getString("username")
  private[this] val DefaultPassword = domainDbConfig.getString("default-password")

  private[this] val domainStore: DomainStore = new DomainStore(dbPool)

  private[this] val domainDBContoller =
    new DomainDBController(orientDbConfig, domainDbConfig, context.system)

  def receive: Receive = {
    case createRequest: CreateDomainRequest => createDomain(createRequest)
    case deleteRequest: DeleteDomainRequest => deleteDomain(deleteRequest)
    case updateRequest: UpdateDomainRequest => updateDomain(updateRequest)
    case getRequest: GetDomainRequest => getDomain(getRequest)
    case listRequest: ListDomainsRequest => listDomains(listRequest)
    case message: Any => unhandled(message)
  }

  def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, ownerUsername, importFile) = createRequest
    val dbName = UUID.randomUUID().getLeastSignificantBits().toString()

    // TODO we should be optionally randomizing the password and passing it in.
    val password = DefaultPassword

    val domainFqn = DomainFqn(namespace, domainId)
    
    val result = domainStore.createDomain(
        domainFqn,
        displayName,
        ownerUsername,
      DomainDatabaseInfo(dbName,
      Username,
      password))

    reply(result)

    // TODO opportunity for some scala fu here to actually add try like map and foreach
    // functions to the CreateResult class.
    result foreach {
      case CreateSuccess(()) =>
        domainDBContoller.createDomain(dbName, password, importFile) onComplete {
          case Success(()) =>
            domainStore.getDomainByFqn(domainFqn) map (_.map { domain =>
              val updated = domain.copy(status = DomainStatus.Online)
              domainStore.updateDomain(updated)
            })
          case Failure(f) =>
            // TODO we should probably have some field on the domain that references errors?
            domainStore.getDomainByFqn(domainFqn) map (_.map { domain =>
              val updated = domain.copy(status = DomainStatus.Error)
              domainStore.updateDomain(updated)
            })
        }
      case _ =>
    }
  }

  def updateDomain(request: UpdateDomainRequest): Unit = {
    val UpdateDomainRequest(namespace, domainId, displayName) = request
    reply(
      domainStore.getDomainByFqn(DomainFqn(namespace, domainId)).flatMap {
        case Some(domain) =>
          val updated = domain.copy(displayName = displayName)
          domainStore.updateDomain(updated)
        case None =>
          Success(NotFound)
      })
  }

  def deleteDomain(deleteRequest: DeleteDomainRequest): Unit = {
    val DeleteDomainRequest(namespace, domainId) = deleteRequest
    val domainFqn = DomainFqn(namespace, domainId)
    val domain = domainStore.getDomainByFqn(domainFqn)
    val databaseConfig = domainStore.getDomainDatabaseInfo(domainFqn)
    reply((domain, databaseConfig) match {
      case (Success(Some(domain)), Success(Some(databaseConfig))) => {
        domainStore.removeDomain(domainFqn)
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
    reply(domainStore.getDomainsByOwner(listRequest.username))
  }
}

object DomainStoreActor {
  def props(dbPool: OPartitionedDatabasePool,
    ec: ExecutionContext): Props = Props(new DomainStoreActor(dbPool, ec))

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, owner: String, importFile: Option[String])
  case class UpdateDomainRequest(namespace: String, domainId: String, displayName: String)
  case class DeleteDomainRequest(namespace: String, domainId: String)
  case class GetDomainRequest(namespace: String, domainId: String)
  case class ListDomainsRequest(username: String)
}
