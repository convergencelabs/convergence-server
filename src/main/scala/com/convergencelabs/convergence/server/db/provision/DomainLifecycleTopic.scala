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

package com.convergencelabs.convergence.server.db.provision

import akka.actor.typed.Behavior
import akka.actor.typed.pubsub.Topic
import akka.actor.typed.pubsub.Topic.Command
import com.convergencelabs.convergence.server.domain.DomainId

object DomainLifecycleTopic {
  sealed trait Message

  case class DomainDeleted(domainId: DomainId) extends Message

  val TopicName: String = "DomainLifecycle"

  val TopicBehavior: Behavior[Command[Message]] = Topic[Message](TopicName)

  type TopicMessage = Command[Message]
}
