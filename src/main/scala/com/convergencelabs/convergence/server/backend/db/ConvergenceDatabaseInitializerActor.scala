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

package com.convergencelabs.convergence.server.backend.db

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

/**
 * A utility actor that will ensure that the Convergence database is
 * initialized.  This actor will be a singleton created by the
 * backend.
 */
private[server] object ConvergenceDatabaseInitializerActor extends Logging {

  sealed trait Command extends CborSerializable

  final case class AssertInitialized(replyTo: ActorRef[InitializationResponse]) extends Command

  sealed trait InitializationResponse

  final case class Initialized() extends InitializationResponse
  final case class InitializationFailed(cause: Throwable) extends InitializationResponse

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      debug("ConvergenceDatabaseInitializerActor starting up")

      val initializer = new ConvergenceDatabaseInitializer(
        context.system.settings.config,
        context.executionContext
      )

      Behaviors.receiveMessage[Command] {
        case AssertInitialized(replyTo) =>
          initializer.assertInitialized() match {
            case Failure(cause) =>
              replyTo ! InitializationFailed(cause)
            case Success(_) =>
              replyTo ! Initialized()
          }
          Behaviors.same
      }
    }
  }
}
