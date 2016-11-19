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
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.ActorLogging
import akka.actor.Props
import akka.pattern.ask
import scala.util.Try
import scala.concurrent.ExecutionContext
import com.convergencelabs.server.domain.DomainStatus
import java.util.UUID
import com.convergencelabs.server.domain.DomainDatabaseInfo
import java.io.StringWriter
import java.io.PrintWriter
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainsForUserRequest
import akka.actor.ActorRef
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import akka.util.Timeout
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainProvisioned
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DestroyDomain
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainDeleted
import com.convergencelabs.server.db.provision.DomainProvisionerActor

class DomainStoreActor private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] val domainProvisioner: ActorRef)
    extends StoreActor with ActorLogging {

  private[this] val RandomizeCredentials = context.system.settings.config.getBoolean("convergence.domain-databases.randomize-credentials")

  private[this] val domainStore: DomainStore = new DomainStore(dbPool)
  private[this] implicit val ec = context.system.dispatcher

  def receive: Receive = {
    case createRequest: CreateDomainRequest => createDomain(createRequest)
    case deleteRequest: DeleteDomainRequest => deleteDomain(deleteRequest)
    case updateRequest: UpdateDomainRequest => updateDomain(updateRequest)
    case getRequest: GetDomainRequest => getDomain(getRequest)
    case listRequest: ListDomainsRequest => listDomains(listRequest)
    case deleteForUser: DeleteDomainsForUserRequest => deleteDomainsForUser(deleteForUser)
    case message: Any => unhandled(message)
  }

  def createDomain(createRequest: CreateDomainRequest): Unit = {
    val CreateDomainRequest(namespace, domainId, displayName, ownerUsername) = createRequest
    val dbName = Math.abs(UUID.randomUUID().getLeastSignificantBits()).toString()

    val (adminUsername, adminPassword, normalUsername, normalPassword) = RandomizeCredentials match {
      case false =>
        ("admin", "admin", "writer", "writer")
      case true =>
        (UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString())
    }

    val domainDbInfo = DomainDatabaseInfo(
      dbName,
      normalUsername,
      normalPassword,
      adminUsername,
      adminPassword)

    val domainFqn = DomainFqn(namespace, domainId)

    val result = domainStore.createDomain(
      domainFqn,
      displayName,
      ownerUsername,
      domainDbInfo)

    reply(result)

    // TODO opportunity for some scala fu here to actually add try like map and foreach
    // functions to the CreateResult class.
    result foreach {
      case CreateSuccess(()) =>
        implicit val requstTimeout = Timeout(4 minutes) // FXIME hardcoded timeout
        val message = ProvisionDomain(dbName, normalUsername, normalPassword, adminUsername, adminPassword)
        (domainProvisioner ? message).mapTo[DomainProvisioned] onComplete {
          case Success(DomainProvisioned()) =>
            log.debug(s"Domain created, setting status to online: $dbName")
            domainStore.getDomainByFqn(domainFqn) map (_.map { domain =>
              val updated = domain.copy(status = DomainStatus.Online)
              domainStore.updateDomain(updated)
            })
          case Failure(f) =>
            log.error(f, s"Domain was not created successfully: $dbName")

            val sr = new StringWriter();
            val w = new PrintWriter(sr);
            f.printStackTrace(w);
            val statusMessage = sr.getBuffer.toString();
            sr.close();
            w.close()

            domainStore.getDomainByFqn(domainFqn) map (_.map { domain =>
              val updated = domain.copy(status = DomainStatus.Error, statusMessage = statusMessage)
              domainStore.updateDomain(updated)
            })
        }
      case _ =>
      // FIXME what happens here?
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
        // FIXME we don't seem to care about the response?
        implicit val requstTimeout = Timeout(4 minutes) // FXIME hardcoded timeout
        (domainProvisioner ? DestroyDomain(databaseConfig.database))
        Success(DeleteSuccess)
      }
      case _ => Success(NotFound)
    })
  }

  def deleteDomainsForUser(request: DeleteDomainsForUserRequest): Unit = {
    val DeleteDomainsForUserRequest(username) = request
    log.debug(s"Deleting domains for user: ${username}")

    domainStore.getAllDomainInfoForUser(username) map { domains =>
      // FIXME we need to review what happens when something fails.
      // we will eventually delete the user and then we won't be
      // able to look up the domains again.
      sender ! (())

      domains.foreach {
        case (domain, info) =>
          log.debug(s"Deleting domain database for ${domain.domainFqn}: ${info.database}")
          // FIXME we don't seem to care about the response?
          implicit val requstTimeout = Timeout(4 minutes) // FXIME hardcoded timeout
          (domainProvisioner ? DestroyDomain(info.database)) onComplete {
            case Success(_) =>
              log.debug(s"Domain database deleted: ${info.database}")
              log.debug(s"Removing domain record: ${domain.domainFqn}")
              domainStore.removeDomain(domain.domainFqn) match {
                case Success(_) =>
                  log.debug(s"Domain record removed: ${domain.domainFqn}")
                case Failure(cause) =>
                  log.error(cause, s"Error deleting domain record: ${domain.domainFqn}")
              }
            case Failure(f) =>
              log.error(f, s"Could not desstroy domain database: ${domain.domainFqn}")
          }
      }
    } recover {
      case cause: Exception =>
        log.error(cause, s"Error deleting domains for user: ${username}")
    }
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
    provisionerActor: ActorRef): Props =
    Props(new DomainStoreActor(dbPool, provisionerActor))

  case class CreateDomainRequest(namespace: String, domainId: String, displayName: String, owner: String)
  case class UpdateDomainRequest(namespace: String, domainId: String, displayName: String)
  case class DeleteDomainRequest(namespace: String, domainId: String)
  case class GetDomainRequest(namespace: String, domainId: String)
  case class ListDomainsRequest(username: String)
  case class DeleteDomainsForUserRequest(username: String)
}
