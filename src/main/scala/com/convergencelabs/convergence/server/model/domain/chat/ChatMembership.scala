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

package com.convergencelabs.convergence.server.model.domain.chat

import com.fasterxml.jackson.core.`type`.TypeReference

import scala.util.{Failure, Success, Try}

object ChatMembership extends Enumeration {
  val Public, Private = Value

  def parse(s: String): Try[ChatMembership.Value] = values.find(_.toString.toLowerCase() == s.toLowerCase()) match {
    case Some(v) => Success(v)
    case None => Failure(InvalidChatMembershipValue(s))
  }

  final case class InvalidChatMembershipValue(value: String) extends RuntimeException("Invalid ChatMembership string: " + value)
}


// Used for serialization via Jackson
class ChatMembershipTypeReference extends TypeReference[ChatType.type] {}
