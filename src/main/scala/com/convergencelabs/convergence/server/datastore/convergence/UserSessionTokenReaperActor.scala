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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import grizzled.slf4j.Logging

import scala.language.postfixOps

object UserSessionTokenReaperActor extends Logging {

  trait Message

  private case object CleanUpSessions extends Message

  def apply(dbProvider: DatabaseProvider): Behavior[Message] = {
    debug(msg = "UserSessionTokenReaperActor initializing")

    val userSessionTokenStore = new UserSessionTokenStore(dbProvider)

    Behaviors.receiveMessage[Message] {
      case CleanUpSessions =>
        debug(msg = "Cleaning expired user session tokens")
        userSessionTokenStore.cleanExpiredTokens() recover {
          case cause: Throwable =>
            error(msg = "Error cleaning up expired user session tokens", cause)
        }
        Behaviors.same
    }.receiveSignal {
      case (_, PostStop) =>
        debug(msg = "UserSessionTokenReaperActor stopping")
        Behaviors.same
    }
  }
}
