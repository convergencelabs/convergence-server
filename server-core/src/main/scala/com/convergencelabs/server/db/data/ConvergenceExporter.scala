package com.convergencelabs.server.db.data

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.DomainDBProvider
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

import akka.actor.ActorRef
import grizzled.slf4j.Logging

class ConvergenceExporter(
    private[this] val dbBaseUri: String,
    private[this] val dbPool: OPartitionedDatabasePool) extends Logging {

  val dbProvider = new DomainDBProvider(dbBaseUri, dbPool)
  
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
    val userStore = new UserStore(dbPool, Duration.ofMillis(0L))

    val user = for {
      user <- userStore.getUserByUsername(username)
      pwHash <- userStore.getDomainUserPasswordHash(username)
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
    val domainStore = new DomainStore(dbPool)
    domainStore.getAllDomainInfoForUser(username) map {
      _.map { case (domain, dbInfo) =>
        // FIXME error handling
        val pool = dbProvider.getDomainDBPool(domain.domainFqn).get.get
        val provider = new DomainPersistenceProvider(pool)
        val exporter = new DomainExporter(provider)
        // FIXME error handling
        val domainScript = exporter.exportDomain().get
        val result = CreateDomain(
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.displayName,
            domain.status.toString().toLowerCase(),
            domain.statusMessage,
            domain.owner.username,
            Some(domainScript)
            )
        result
      }
    }
  }
}