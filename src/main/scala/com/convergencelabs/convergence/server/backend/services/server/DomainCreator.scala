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

package com.convergencelabs.convergence.server.backend.services.server

import java.util.UUID

import com.convergencelabs.convergence.server.backend.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.backend.datastore.convergence._
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager.DomainDatabaseCreationData
import com.convergencelabs.convergence.server.backend.services.server.DomainDatabaseManagerActor.CreateDomainDatabaseResponse
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.{DomainDatabase, DomainStatus}
import com.convergencelabs.convergence.server.model.server.role.DomainRoleTarget
import com.convergencelabs.convergence.server.security.Roles
import com.convergencelabs.convergence.server.util.ExceptionUtils
import com.typesafe.config.Config
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
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
  import DomainCreator._

  def createDomain(domainId: DomainId,
                   displayName: String,
                   owner: String): Try[DomainDatabase] = {

    val dbName = Math.abs(UUID.randomUUID().getLeastSignificantBits).toString
    val (dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) = if (randomizeCredentials) {
      (UUID.randomUUID().toString, UUID.randomUUID().toString,
        UUID.randomUUID().toString, UUID.randomUUID().toString)
    } else {
      ("writer", "writer", "admin", "admin")
    }

    val domainDbInfo = DomainDatabase(dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword)

    for {
      _ <- validate(domainId)
      _ <- domainStore.createDomain(domainId, displayName, domainDbInfo)
        .recoverWith {
          case DuplicateValueException(field, _, _) =>
            Failure(DomainAlreadyExists(field))
          case _: NamespaceNotFoundException =>
            Failure(NamespaceNotFoundError())
        }
      _ <- roleStore.setUserRolesForTarget(owner, DomainRoleTarget(domainId), Set(Roles.Domain.Owner))
    } yield domainDbInfo
  }

  def createDomainDatabase(domainId: DomainId, anonymousAuth: Boolean, database: DomainDatabase): Future[Unit] = {
    val DomainDatabase(dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) = database
    val provisionRequest = DomainDatabaseCreationData(domainId, dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth)
    createDomainDatabase(provisionRequest)
      .map(_.response.fold(
        { e =>
          error(s"Domain was not provisioned successfully: $dbName")
          val msg = e.message.getOrElse("Unknown error provisioning the domain")
          this.updateStatusAfterProvisioning(domainId, DomainStatus.Error, msg)
        },
        { _ =>
          debug(s"Domain provisioned, setting status to online: $dbName")
          this.updateStatusAfterProvisioning(domainId, DomainStatus.Online)
        },
      ))
      .recover {
        case cause: Throwable =>
          error(s"Domain was not provisioned successfully: $dbName", cause)
          val statusMessage = ExceptionUtils.stackTraceToString(cause)
          this.updateStatusAfterProvisioning(domainId, DomainStatus.Error, statusMessage)
          ()
      }
  }

  private[this] def validate(domainId: DomainId): Try[Unit] = {
    val DomainId(namespace, id) = domainId
    if (namespace.isEmpty) {
      Failure(InvalidDomainValue("The namespace can not be empty"))
    } else if (id.isEmpty) {
      Failure(InvalidDomainValue("The domain id can not be empty"))
    } else {
      val keys = List(Namespaces.Enabled, Namespaces.UserNamespacesEnabled, Namespaces.DefaultNamespace)
      configStore.getConfigs(keys).flatMap { configs =>
        val namespacesEnabled = configs(Namespaces.Enabled).asInstanceOf[Boolean]
        val userNamespacesEnabled = configs(Namespaces.UserNamespacesEnabled).asInstanceOf[Boolean]
        val defaultNamespace = configs(Namespaces.DefaultNamespace).asInstanceOf[String]
        if (!namespacesEnabled && namespace != defaultNamespace) {
          Failure(InvalidDomainValue("When namespaces are disabled, you can only create domains in the default namespace."))
        } else if (!userNamespacesEnabled && namespace.startsWith("~")) {
          Failure(InvalidDomainValue("User namespaces are disabled."))
        } else {
          Success(())
        }
      }
    }
  }

  private[this] def updateStatusAfterProvisioning(domainId: DomainId, status: DomainStatus.Value, statusMessage: String = ""): Unit = {
    domainStore
      .updateDomainStatus(domainId, status, statusMessage)
      .recover {
        case cause: Throwable =>
          logger.error("Could not update domain status after creation", cause)
      }
  }

  protected def createDomainDatabase(creationData: DomainDatabaseCreationData): Future[CreateDomainDatabaseResponse]
}

object DomainCreator {

  sealed trait DomainCreationError

  final case class DomainAlreadyExists(field: String) extends RuntimeException with NoStackTrace with DomainCreationError

  final case class InvalidDomainValue(message: String) extends RuntimeException(message) with NoStackTrace with DomainCreationError

  final case class UnknownError() extends RuntimeException with NoStackTrace with DomainCreationError

  final case class NamespaceNotFoundError() extends RuntimeException with NoStackTrace with DomainCreationError

}
