/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.convergence

import java.util.UUID

import com.convergencelabs.convergence.server.datastore.InvalidValueException
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.db.provision.DomainProvisioner.ProvisionRequest
import com.convergencelabs.convergence.server.domain.{DomainDatabase, DomainId, DomainStatus}
import com.convergencelabs.convergence.server.security.Roles
import com.convergencelabs.convergence.server.util.ExceptionUtils
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A utility class that knows how to create Convergence Domains. This class is an
 * abstract class that implements the main logic of creating a domain but leaves
 * the details of how the domain database is created to subclasses.
 *
 * @param dbProvider       The database provider that will produce a database connection.
 * @param config           The Convergence Server config.
 * @param executionContext An execution context for asynchronous operations.
 */
abstract class DomainCreator(dbProvider: DatabaseProvider,
                             config: Config,
                             implicit val executionContext: ExecutionContext) extends Logging {

  private[this] val domainStore = new DomainStore(dbProvider)
  private[this] val configStore = new ConfigStore(dbProvider)
  private[this] val roleStore = new RoleStore(dbProvider)
  private[this] val randomizeCredentials = config.getBoolean("convergence.persistence.domain-databases.randomize-credentials")

  import ConfigKeys._

  def createDomain(namespace: String,
                   id: String,
                   displayName: String,
                   anonymousAuth: Boolean,
                   owner: String): Future[DomainDatabase] = {

    val dbName = Math.abs(UUID.randomUUID().getLeastSignificantBits).toString
    val (dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) = if (randomizeCredentials) {
      (UUID.randomUUID().toString, UUID.randomUUID().toString,
        UUID.randomUUID().toString, UUID.randomUUID().toString)
    } else {
      ("writer", "writer", "admin", "admin")
    }

    val domainId = DomainId(namespace, id)
    val domainDbInfo = DomainDatabase(dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword)

    val provisionRequest = ProvisionRequest(domainId, dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth)
    for {
      _ <- FutureUtils.tryToFuture(validate(namespace, id))
      _ <- FutureUtils.tryToFuture(domainStore.createDomain(domainId, displayName, domainDbInfo))
      _ <- provisionDomain(provisionRequest)
        .map { _ =>
          roleStore.setUserRolesForTarget(owner, DomainRoleTarget(domainId), Set(Roles.Domain.Owner))
        }
        .map { _ =>
          debug(s"Domain created, setting status to online: $dbName")
          this.updateStatusAfterProvisioning(domainId, DomainStatus.Online)
        }
        .recover {
          case cause: Throwable =>
            error(s"Domain was not created successfully: $dbName", cause)
            val statusMessage = ExceptionUtils.stackTraceToString(cause)
            this.updateStatusAfterProvisioning(domainId, DomainStatus.Error, statusMessage)
            ()
        }
    } yield domainDbInfo
  }

  private[this] def validate(namespace: String, id: String): Try[Unit] = {
    if (namespace.isEmpty) {
      Failure(InvalidValueException("namespace", "The namespace can not be empty"))
    } else if (id.isEmpty) {
      Failure(InvalidValueException("id", "The domain id can not be empty"))
    } else {
      val keys = List(Namespaces.Enabled, Namespaces.UserNamespacesEnabled, Namespaces.DefaultNamespace)
      configStore.getConfigs(keys).flatMap { configs =>
        val namespacesEnabled = configs(Namespaces.Enabled).asInstanceOf[Boolean]
        val userNamespacesEnabled = configs(Namespaces.UserNamespacesEnabled).asInstanceOf[Boolean]
        val defaultNamesapce = configs(Namespaces.DefaultNamespace).asInstanceOf[String]
        if (!namespacesEnabled && namespace != defaultNamesapce) {
          Failure(InvalidValueException("namespace", "When namespaces are disabled, you can only create domains in the default namespace."))
        } else if (!userNamespacesEnabled && namespace.startsWith("~")) {
          Failure(InvalidValueException("namespace", "User namespaces are disabled."))
        } else {
          Success(())
        }
      }
    }
  }

  private[this] def updateStatusAfterProvisioning(domainId: DomainId, status: DomainStatus.Value, statusMessage: String = ""): Unit = {
    domainStore
      .getDomainByFqn(domainId)
      .flatMap {
        case Some(domain) =>
          val updated = domain.copy(status = status, statusMessage = statusMessage)
          domainStore.updateDomain(updated)
        case None =>
          Failure(new IllegalStateException("Could not find domain after it was created to update its status."))
      }
      .recover {
        case cause: Throwable =>
          logger.error("Could not update domain status after creation", cause)
      }
  }

  def provisionDomain(request: ProvisionRequest): Future[Unit]
}