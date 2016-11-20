package com.convergencelabs.server.db.data

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.schema.DomainDBProvider
import scala.util.Try
import java.time.Duration
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.domain.DomainDatabaseInfo
import com.convergencelabs.server.domain.DomainFqn
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import scala.language.postfixOps
import com.convergencelabs.server.db.provision.DomainProvisionerActor.DomainProvisioned
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import grizzled.slf4j.Logging
import java.util.UUID
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess

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
          userData.lastName.getOrElse(""))
        userStore.createUser(user, userData.password) recover { case e: Exception => throw e }
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
        logger.debug(s"Requestion domain provisioning for: ${domainData.namespace}/${domainData.domainId}")
        val response = (domainStoreActor ? domainCreateRequest).mapTo[CreateResult[DomainDatabaseInfo]]

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
        }

        response onFailure {
          case cause: Exception =>
            logger.error("Domain provisioing failed", cause)
        }
      }
    }
  }
}