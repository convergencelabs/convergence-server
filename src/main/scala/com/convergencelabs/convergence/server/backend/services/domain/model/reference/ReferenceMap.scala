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

package com.convergencelabs.convergence.server.backend.services.domain.model.reference

import com.convergencelabs.convergence.server.model.domain.session.DomainSessionId

import scala.collection.mutable.ListBuffer

/**
 * A utility class that stores references by session and key. Each session owns
 * a set of references. Each reference owned by a session has a unique key.
 */
private[reference] class ReferenceMap {

  /**
   * Stores references as a nested map. First by sessionId and then by key.
   * This allows us to get a reference by session and key, but also easily
   * remove all references for a session when a session leaves.
   */
  private[this] val references =
    collection.mutable.Map[DomainSessionId, collection.mutable.Map[String, ModelReference[_, _]]]()

  /**
   * Determines if a reference is set for a given session and key.
   *
   * @param sessionId The id of the session that owns the reference.
   * @param key       The reference key
   * @return True if the map contains a references for the session and key,
   *         false oterwise.
   */
  def has(sessionId: DomainSessionId, key: String): Boolean = {
    this.references.get(sessionId) match {
      case Some(map) =>
        map.contains(key)
      case None =>
        false
    }
  }

  /**
   * Puts a new reference. It will be stored by the session and key.
   *
   * @param reference The reference to put.
   */
  def put(reference: ModelReference[_, _]): Unit = {
    val session: DomainSessionId = reference.session
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

  /**
   * Gets a reference for a specific session and key.
   *
   * @param sessionId The session owning the reference.
   * @param key       The key of the reference.
   * @return The reference for the session and key, or None if it does not
   *         exist.
   */
  def get(sessionId: DomainSessionId, key: String): Option[ModelReference[_, _]] = {
    this.references.get(sessionId).flatMap { sr => sr.get(key) }
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

  /**
   * Removes all references in the map.
   */
  def removeAll(): Unit = {
    this.references.clear()
  }

  /**
   * Removes a single reference from the map.
   *
   * @param sessionId The id of the sessions that owns the reference.
   * @param key       The key of the reference.
   * @return The removed reference if it exists or None if it does not.
   */
  def remove(sessionId: DomainSessionId, key: String): Option[ModelReference[_, _]] = {
    val result = this.get(sessionId, key)
    if (result.isDefined) {
      references(sessionId) -= key
    }
    result
  }

  /**
   * Removes all references owned by the specified session.
   *
   * @param sessionId The id of the session to remove references for.
   */
  def removeAllReferencesForSession(sessionId: DomainSessionId): Unit = {
    references -= sessionId
  }
}
