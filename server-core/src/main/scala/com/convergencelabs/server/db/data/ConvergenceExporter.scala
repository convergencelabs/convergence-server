package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.UserStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorRef
import grizzled.slf4j.Logging
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.DomainDatabaseFactory
import com.convergencelabs.server.datastore.domain.DomainPersistenceProviderImpl

class ConvergenceExporter(
    private[this] val dbBaseUri: String,
    private[this] val dbProvider: DatabaseProvider) extends Logging {

  val dbFactory = new DomainDatabaseFactory(dbBaseUri, dbProvider)
  
  def exportData(username: String): Try[ConvergenceScript] = {
    for {
      user <- exportUser(username)
      domains <- exportDomains(username)
    } yield {
      ConvergenceScript(Some(List(user)), Some(domains))
    }
  }

  private[this] def exportUser(username: String): Try[CreateConvergenceUser] = {
    logger.debug("Importing convergence user")
    val userStore = new UserStore(dbProvider, Duration.ofMillis(0L))

    val user = for {
      user <- userStore.getUserByUsername(username)
      pwHash <- userStore.getUserPasswordHash(username)
    } yield (user, pwHash)

    user flatMap {
      case (Some(user), Some(hash)) =>
        val createUser = CreateConvergenceUser(
          user.username,
          SetPassword("hash", hash),
          user.email,
          Some(user.firstName),
          Some(user.lastName),
          None)
        logger.debug("Done exporing convergence user")
        Success(createUser)
      case _ =>
        Failure(throw new IllegalArgumentException("Could not find username or password"))
    }
  }

  private[this] def exportDomains(username: String): Try[List[CreateDomain]] = {
    logger.debug(s"Exporting domains for user: ${username}")
    val domainStore = new DomainStore(dbProvider)
    domainStore.getDomainsByOwner(username) map {
      _.map { case domain =>
        // FIXME error handling
        val pool = dbFactory.getDomainDatabasePool(domain.domainFqn).get
        val provider = new DomainPersistenceProviderImpl(DatabaseProvider(pool))
        val exporter = new DomainExporter(provider)
        // FIXME error handling
        val domainScript = exporter.exportDomain().get
        val result = CreateDomain(
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.displayName,
            domain.status.toString().toLowerCase(),
            domain.statusMessage,
            domain.owner,
            Some(domainScript)
            )
        result
      }
    }
  }
}