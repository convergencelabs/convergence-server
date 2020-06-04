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

package com.convergencelabs.convergence.server.db.data

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.convergencelabs.convergence.server.datastore.convergence.DomainStoreActor.{CreateDomainRequest, CreateDomainResponse}
import com.convergencelabs.convergence.server.datastore.convergence.UserStore.User
import com.convergencelabs.convergence.server.datastore.convergence.{DomainStoreActor, UserStore}
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProviderImpl
import com.convergencelabs.convergence.server.db.{DatabaseProvider, SingleDatabaseProvider}
import com.convergencelabs.convergence.server.domain.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Try}

class ConvergenceImporter(private[this] val dbBaseUri: String,
                          private[this] val convergenceDbProvider: DatabaseProvider,
                          private[this] val domainStoreActor: ActorRef[DomainStoreActor.Message],
                          private[this] val data: ConvergenceScript,
                          private[this] implicit val ec: ExecutionContext,
                          private[this] implicit val system: ActorSystem[_]) extends Logging {

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
    data.domains foreach {
      _.map { domainData =>
        logger.debug(s"Importing domain: ${domainData.namespace}/${domainData.id}")

        // FIXME hardcoded timeout
        implicit val requestTimeout: Timeout = Timeout(4 minutes)
        logger.debug(s"Requesting domain provisioning for: ${domainData.namespace}/${domainData.id}")
        // FIXME Anonymous Auth and OWNER
        domainStoreActor.ask[CreateDomainResponse](CreateDomainRequest(
          domainData.namespace, domainData.id, domainData.displayName, anonymousAuth = false, "owner", _))
          .map(_.dbInfo.fold(
            {
              case DomainStoreActor.DomainAlreadyExistsError(field) =>
                logger.error(s"Domain provisioing failed because the domain a domain with the same value for '$field' already exists.")
              case DomainStoreActor.InvalidDomainCreationRequest(message) =>
                logger.error("Domain provisioing failed because the domain creation data was invalid: " + message)
              case DomainStoreActor.UnknownError() =>
              logger.error("Domain provisioing failed for an unknown reason")
            },
            { dbInfo =>
              logger.debug(s"Domain database provisioned successfully: ${domainData.namespace}/${domainData.id}")
              domainData.dataImport map { script =>
                logger.debug(s"Importing data for domain: ${domainData.namespace}/${domainData.id}")

                val dbProvider = new SingleDatabaseProvider(dbBaseUri, dbInfo.database, dbInfo.username, dbInfo.password)
                val provider = new DomainPersistenceProviderImpl(DomainId(domainData.namespace, domainData.id), dbProvider)
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
            })

          )
      }
    }
  }
}