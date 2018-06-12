package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.UserStore.User
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.convergnece.DomainStore
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainDatabase
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.datastore.domain.DomainPersistenceProviderImpl

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
    val userStore = new UserStore(convergenceDbProvider, Duration.ofMillis(0L))
    data.users foreach {
      _.map { userData =>
        logger.debug(s"Importing user: ${userData.username}")
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
    val domainStore = new DomainStore(convergenceDbProvider)
    data.domains foreach {
      _.map { domainData =>
        logger.debug(s"Importing domaing: ${domainData.namespace}/${domainData.id}")

        val domainCreateRequest = CreateDomainRequest(
          domainData.namespace, domainData.id, domainData.displayName, domainData.owner, false)

        // FXIME hardcoded timeout
        implicit val requstTimeout = Timeout(4 minutes)
        logger.debug(s"Requesting domain provisioning for: ${domainData.namespace}/${domainData.id}")
        val response = (domainStoreActor ? domainCreateRequest).mapTo[DomainDatabase]

        response.foreach {
          case dbInfo =>
            logger.debug(s"Domain database provisioned successfuly: ${domainData.namespace}/${domainData.id}")
            domainData.dataImport map { script =>
              logger.debug(s"Importing data for domain: ${domainData.namespace}/${domainData.id}")
              
              val db = new ODatabaseDocumentTx(s"${dbBaseUri}/${dbInfo.database}")
              db.open(dbInfo.username, dbInfo.password)
                
              val provider = new DomainPersistenceProviderImpl(DatabaseProvider(db))
              val domainImporter = new DomainImporter(provider, script)
              domainImporter.importDomain() map { _ =>
                logger.debug("Domain import successful.")
                db.close()
              } recoverWith {
                case cause: Exception =>
                  db.close()
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