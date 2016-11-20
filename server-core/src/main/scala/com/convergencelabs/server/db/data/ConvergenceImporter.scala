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

class ConvergenceImporter(
    private[this] val dbBaseUri: String,
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainProvisioner: ActorRef,
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
        val domainDbInfo = DomainDatabaseInfo(
          domainData.dbName,
          domainData.dbUsername,
          domainData.dbPassword,
          domainData.dbAdminUsername,
          domainData.dbAdminUsername)

        val domainFqn = DomainFqn(domainData.namespace, domainData.domainId)

        domainStore.createDomain(domainFqn, domainData.displayName, domainData.owner, domainDbInfo)

        implicit val requstTimeout = Timeout(4 minutes) // FXIME hardcoded timeout
        val message = ProvisionDomain(
          domainData.dbName,
          domainData.dbUsername,
          domainData.dbPassword,
          domainData.dbAdminUsername,
          domainData.dbAdminUsername)

        logger.debug(s"Requestion domain provisioning for: ${domainData.dbName}")
        (domainProvisioner ? message).mapTo[DomainProvisioned] onComplete {
          case Success(DomainProvisioned()) =>
            logger.debug(s"Domain provisioned successfuly: ${domainData.dbName}")

            domainData.dataImport map { script =>
              val dbPool = new OPartitionedDatabasePool(
                s"${dbBaseUri}/${domainData.dbName}", domainData.dbUsername, domainData.dbPassword)
              val provider = new DomainPersistenceProvider(dbPool)
              val domainImporter = new DomainImporter(provider, script)
              
              domainImporter.importDomain() map { _ =>
                logger.debug("Domain import successful")
              } recover {
                case cause: Exception =>
                  logger.error("Domain import failed", cause)
              }
            }

          case Failure(cause) =>
            logger.error("Domain provisioing failed", cause)
        }

        logger.debug(s"Finished importing domaing: ${domainData.namespace}/${domainData.domainId}")
      }
    }
  }
}