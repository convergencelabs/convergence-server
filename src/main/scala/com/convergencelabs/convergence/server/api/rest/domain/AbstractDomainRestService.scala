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

package com.convergencelabs.convergence.server.api.rest.domain

import akka.actor.typed.Scheduler
import akka.util.Timeout
import com.convergencelabs.convergence.server.util.actor.AskUtils
import com.convergencelabs.convergence.server.api.rest.{JsonSupport, PermissionChecks}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}

import scala.concurrent.ExecutionContext

class AbstractDomainRestService(scheduler: Scheduler,
                                executionContext: ExecutionContext,
                                defaultTimeout: Timeout)
  extends JsonSupport with AskUtils with PermissionChecks {

  protected implicit val ec: ExecutionContext = executionContext
  protected implicit val t: Timeout = defaultTimeout
  protected implicit val s: Scheduler = scheduler

  // Permission Checks

  protected def canManageDomainSettings(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkDomainPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageSettings))
  }

  protected def canManageDomainUsers(domainFqn: DomainId, authProfile: AuthorizationProfile): Boolean = {
    checkDomainPermission(domainFqn, authProfile, Set(Permissions.Domain.ManageUsers))
  }
}
