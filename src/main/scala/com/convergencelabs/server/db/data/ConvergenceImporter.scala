package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.convergence.UserStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainDatabase
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.convergencelabs.server.datastore.domain.DomainPersistenceProviderImpl
import com.convergencelabs.server.db.PooledDatabaseProvider
import com.convergencelabs.server.db.SingleDatabaseProvider

class ConvergenceImporter(
  private[this] val dbBaseUri: String,
  private[this] val convergenceDbProvider: DatabaseProvider,
  private[this] val domainStoreActor: ActorRef,
  private[this] val data: ConvergenceScript,
  private[this] implicit val ec: ExecutionContext) extends Logging {

  def importData(): Try[Unit] = {
    importUsers() flatMap (_ =>
      importDomains())
  }

  def importUsers(): Try[Unit] = Try {
    logger.debug("Importing convergence users")
    val userStore = new UserStore(convergenceDbProvider)
    data.users foreach {
      _.map { userData =>
        logger.debug(s"Importing user: ${userData.username}")
        val user = User(
          userData.username,
          userData.email,
          userData.firstName.getOrElse(""),
          userData.lastName.getOrElse(""),
          userData.displayName.getOrElse(""),
          None)
        userData.password.passwordType match {
          case "plaintext" =>
            userStore.createUser(user, userData.password.value, userData.bearerToken).get
          case "hash" =>
            userStore.createUserWithPasswordHash(user, userData.password.value, userData.bearerToken).get
        }
      }
    }
    logger.debug("Done importing convergence users")
  }

  def importDomains(): Try[Unit] = Try {
    logger.debug("Importing domains")
    val domainStore = new DomainStore(convergenceDbProvider)
    data.domains foreach {
      _.map { domainData =>
        logger.debug(s"Importing domaing: ${domainData.namespace}/${domainData.id}")

        // FIXME Anonynous Auth
        val domainCreateRequest = CreateDomainRequest(
          domainData.namespace, domainData.id, domainData.displayName, false)

        // FXIME hardcoded timeout
        implicit val requstTimeout = Timeout(4 minutes)
        logger.debug(s"Requesting domain provisioning for: ${domainData.namespace}/${domainData.id}")
        val response = (domainStoreActor ? domainCreateRequest).mapTo[DomainDatabase]

        response.foreach {
          case dbInfo =>
            logger.debug(s"Domain database provisioned successfuly: ${domainData.namespace}/${domainData.id}")
            domainData.dataImport map { script =>
              logger.debug(s"Importing data for domain: ${domainData.namespace}/${domainData.id}")

              val dbProvider = new SingleDatabaseProvider(dbBaseUri, dbInfo.database, dbInfo.username, dbInfo.password)
              val provider = new DomainPersistenceProviderImpl(dbProvider)
              val domainImporter = new DomainImporter(provider, script)
              dbProvider
                .connect()
                .flatMap(_ => domainImporter.importDomain())
                .map { _ =>
                  logger.debug("Domain import successful.")
                  dbProvider.shutdown()
                }
                .recoverWith {
                  case cause: Exception =>
                    dbProvider.shutdown()
                    logger.error("Domain import failed", cause)
                    Failure(cause)
                }
            } orElse {
              logger.debug("No data to import, domain provisioing complete")
              None
            }
        }

        response.failed.foreach {
          case cause: Exception =>
            logger.error("Domain provisioing failed", cause)
        }
      }
    }
  }
}