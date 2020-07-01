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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors

import akka.actor.typed.ActorRef

/**
 * The MessageReplyTask creates a non-side-effecting task to eventually
 * reply to an asking actor with a particular message.
 *
 * @param replyTo  The actor to reply to.
 * @param response The response to send.
 *
 * @tparam T The type of message that will be sent, as well as the type
 *           of the ActorRef to send the message to.
 */
case class MessageReplyTask[T](replyTo: ActorRef[T], response: T) {

  /**
   * Execute the side effecting task to send the response to the
   * specified actor.
   */
  def execute(): Unit = {
    replyTo ! response
  }
}
