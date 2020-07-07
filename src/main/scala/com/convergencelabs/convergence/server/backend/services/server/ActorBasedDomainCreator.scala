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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.DomainDatabaseManager.DomainDatabaseCreationData
import com.convergencelabs.convergence.server.backend.services.server.DomainDatabaseManagerActor.{CreateDomainDatabaseRequest, CreateDomainDatabaseResponse}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

class ActorBasedDomainCreator(databaseProvider: DatabaseProvider,
                              config: Config,
                              domainProvisioner: ActorRef[CreateDomainDatabaseRequest],
                              executionContext: ExecutionContext,
                              implicit val scheduler: Scheduler,
                              timeout: Timeout)
  extends DomainCreator(databaseProvider, config, executionContext) {

  override protected def createDomainDatabase(data: DomainDatabaseCreationData): Future[CreateDomainDatabaseResponse] = {
    implicit val t: Timeout = timeout
    domainProvisioner
      .ask[CreateDomainDatabaseResponse](ref => CreateDomainDatabaseRequest(data, ref))
  }
}
