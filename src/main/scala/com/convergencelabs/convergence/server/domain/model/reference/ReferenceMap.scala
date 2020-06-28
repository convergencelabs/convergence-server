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

package com.convergencelabs.convergence.server.domain.model.reference

import com.convergencelabs.convergence.server.domain.DomainUserSessionId

import scala.collection.mutable.ListBuffer

class ReferenceMap {

  // stored by sessionId first, then key.
  private[this] val references =
    collection.mutable.Map[DomainUserSessionId, collection.mutable.Map[String, ModelReference[_, _]]]()

  def has(sessionId: DomainUserSessionId, key: String): Boolean = {
    this.references.get(sessionId) match {
      case Some(map) =>
        map.contains(key)
      case None =>
        false
    }
  }

  def put(reference: ModelReference[_, _]): Unit = {
    val session: DomainUserSessionId = reference.session
    val key: String = reference.key

    val sessionRefs = this.references.get(session) match {
      case Some(map) => map
      case None =>
        this.references(session) = collection.mutable.Map[String, ModelReference[_, _]]()
        this.references(session)
    }

    if (sessionRefs.contains(key)) {
      throw new Error(s"reference with key '$key' already exists for session $session")
    }

    sessionRefs(key) = reference
  }

  def get(session: DomainUserSessionId, key: String): Option[ModelReference[_, _]] = {
    this.references.get(session).flatMap { sr => sr.get(key) }
  }

  def getAll: Set[ModelReference[_, _]] = {
    val buffer = ListBuffer[ModelReference[_, _]]()
    references.foreach {
      case (_, sessionRefs) =>
        sessionRefs.foreach {
          case (_, ref) =>
            buffer += ref
        }
    }
    buffer.toSet
  }

  def removeAll(): Unit = {
    this.references.clear()
  }

  def remove(session: DomainUserSessionId, key: String): Option[ModelReference[_, _]] = {
    val result = this.get(session, key)
    if (result.isDefined) {
      references(session) -= key
    }
    result
  }

  def removeBySession(session: DomainUserSessionId): Unit = {
    references -= session
  }
}
