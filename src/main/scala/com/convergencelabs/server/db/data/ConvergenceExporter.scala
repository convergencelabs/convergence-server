/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
    val userStore = new UserStore(dbProvider)

    (for {
      user <- userStore.getUserByUsername(username)
      pwHash <- userStore.getUserPasswordHash(username)
      bearerToken <- userStore.getBearerToken(username)
    } yield (user, pwHash, bearerToken)).flatMap(_ match {
      case (Some(user), Some(hash), Some(bearerToken)) =>
        val createUser = CreateConvergenceUser(
          user.username,
          SetPassword("hash", hash),
          bearerToken,
          user.email,
          Some(user.firstName),
          Some(user.lastName),
          None)
        logger.debug("Done exporing convergence user")
        Success(createUser)
      case _ =>
        Failure(throw new IllegalArgumentException("Could not find username or password"))
    })
  }

  private[this] def exportDomains(namespace: String): Try[List[CreateDomain]] = {
    logger.debug(s"Exporting domains for namespace: ${namespace}")
    val domainStore = new DomainStore(dbProvider)
    domainStore.getDomainsInNamespace(namespace) map {
      _.map { case domain =>
        // FIXME error handling
        val dbProvider = dbFactory.getDomainDatabasePool(domain.domainFqn).get
        val provider = new DomainPersistenceProviderImpl(dbProvider)
        val exporter = new DomainExporter(provider)
        // FIXME error handling
        val domainScript = exporter.exportDomain().get
        val result = CreateDomain(
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.displayName,
            domain.status.toString().toLowerCase(),
            domain.statusMessage,
            Some(domainScript)
            )
        result
      }
    }
  }
}