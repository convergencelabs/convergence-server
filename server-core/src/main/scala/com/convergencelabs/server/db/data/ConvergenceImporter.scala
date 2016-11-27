package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainDatabase
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging

class ConvergenceImporter(
    private[this] val dbBaseUri: String,
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainStoreActor: ActorRef,
    private[this] val data: ConvergenceScript,
    private[this] implicit val ec: ExecutionContext) extends Logging {

  def importData(): Try[Unit] = {
    importUsers() flatMap (_ =>
      importDomains())
  }

  def importUsers(): Try[Unit] = Try {
    logger.debug("Importing convergence users")
    val userStore = new UserStore(dbPool, Duration.ofMillis(0L))
    data.users foreach {
      _.map { userData =>
        val user = User(
          userData.username,
          userData.email,
          userData.firstName.getOrElse(""),
          userData.lastName.getOrElse(""),
          userData.displayName.getOrElse(""))
          userData.password.passwordType match {
          case "plaintext" =>
            userStore.createUser(user, userData.password.value).get
          case "hash" =>
            userStore.createUserWithPasswordHash(user, userData.password.value).get
        }
      }
    }
    logger.debug("Done importing convergence users")
  }

  def importDomains(): Try[Unit] = Try {
    logger.debug("Importing domains")
    val domainStore = new DomainStore(dbPool)
    data.domains foreach {
      _.map { domainData =>
        logger.debug(s"Importing domaing: ${domainData.namespace}/${domainData.domainId}")

        val domainCreateRequest = CreateDomainRequest(
          domainData.namespace, domainData.domainId, domainData.displayName, domainData.owner)

        // FXIME hardcoded timeout
        implicit val requstTimeout = Timeout(4 minutes)
        logger.debug(s"Requesting domain provisioning for: ${domainData.namespace}/${domainData.domainId}")
        val response = (domainStoreActor ? domainCreateRequest).mapTo[CreateResult[DomainDatabase]]

        response onSuccess {
          case CreateSuccess(dbInfo) =>
            logger.debug(s"Domain database provisioned successfuly: ${domainData.namespace}/${domainData.domainId}")
            domainData.dataImport map { script =>
              logger.debug(s"Importing data for domain: ${domainData.namespace}/${domainData.domainId}")
              val dbPool = new OPartitionedDatabasePool(
                s"${dbBaseUri}/${dbInfo.database}", dbInfo.username, dbInfo.password)
              val provider = new DomainPersistenceProvider(dbPool)
              val domainImporter = new DomainImporter(provider, script)

              domainImporter.importDomain() map { _ =>
                logger.debug("Domain import successful.")
              } recover {
                case cause: Exception =>
                  logger.error("Domain import failed", cause)
                  Failure(cause)
              }
            } orElse {
              logger.debug("No data to import, domain provisioing complete")
              None
            }
          case InvalidValue =>
            logger.error("could not create domain due to an invalid value")
          case DuplicateValue =>
            logger.error("could not create domain, because a domain already exists")
        }

        response onFailure {
          case cause: Exception =>
            logger.error("Domain provisioing failed", cause)
        }
      }
    }
  }
}