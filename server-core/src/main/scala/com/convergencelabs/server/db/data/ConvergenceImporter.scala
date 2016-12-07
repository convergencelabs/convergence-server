package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.User
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
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
    private[this] val dbProvider: DatabaseProvider,
    private[this] val domainStoreActor: ActorRef,
    private[this] val data: ConvergenceScript,
    private[this] implicit val ec: ExecutionContext) extends Logging {

  def importData(): Try[Unit] = {
    importUsers() flatMap (_ =>
      importDomains())
  }

  def importUsers(): Try[Unit] = Try {
    logger.debug("Importing convergence users")
    val userStore = new UserStore(dbProvider, Duration.ofMillis(0L))
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
    val domainStore = new DomainStore(dbProvider)
    data.domains foreach {
      _.map { domainData =>
        logger.debug(s"Importing domaing: ${domainData.namespace}/${domainData.id}")

        val domainCreateRequest = CreateDomainRequest(
          domainData.namespace, domainData.id, domainData.displayName, domainData.owner)

        // FXIME hardcoded timeout
        implicit val requstTimeout = Timeout(4 minutes)
        logger.debug(s"Requesting domain provisioning for: ${domainData.namespace}/${domainData.id}")
        val response = (domainStoreActor ? domainCreateRequest).mapTo[DomainDatabase]

        response onSuccess {
          case dbInfo =>
            logger.debug(s"Domain database provisioned successfuly: ${domainData.namespace}/${domainData.id}")
            domainData.dataImport map { script =>
              logger.debug(s"Importing data for domain: ${domainData.namespace}/${domainData.id}")
              val dbPool = new OPartitionedDatabasePool(
                s"${dbBaseUri}/${dbInfo.database}", dbInfo.username, dbInfo.password)
              val provider = new DomainPersistenceProvider(dbProvider)
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
        }

        response onFailure {
          case cause: Exception =>
            logger.error("Domain provisioing failed", cause)
        }
      }
    }
  }
}