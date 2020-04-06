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

package com.convergencelabs.convergence.server.db

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import com.convergencelabs.convergence.server.db.ConvergenceDatabaseInitializerActor._

import scala.util.{Failure, Success}

object ConvergenceDatabaseInitializerActor {
  def props(): Props = Props(new ConvergenceDatabaseInitializerActor())

  final case class AssertInitialized()

  sealed trait InitializationResponse

  final case class Initialized() extends InitializationResponse
}

class ConvergenceDatabaseInitializerActor() extends Actor with ActorLogging {
  private[this] val initializer = new ConvergenceDatabaseInitializer(
    this.context.system.settings.config,
    this.context.dispatcher
  )

  def receive: Receive = {
    case AssertInitialized() =>
      initializer.initialize() match {
        case Failure(cause) =>
          sender ! Status.Failure(cause)
        case Success(_) =>
          sender ! Status.Success(())
      }
    case msg: Any =>
      unhandled(msg)
  }
}
