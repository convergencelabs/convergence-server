package com.convergencelabs.server.domain.model.reference

import scala.collection.mutable.ListBuffer
import com.convergencelabs.server.domain.model.SessionKey

class ReferenceMap {

  // stored by sessionId first, then key.
  private[this] val references =
    collection.mutable.Map[SessionKey, collection.mutable.Map[String, ModelReference[_]]]()

  def put(reference: ModelReference[_]): Unit = {
    val session: SessionKey = reference.session
    val key: String = reference.key;

    val sessionRefs = this.references.get(session) match {
      case Some(map) => map
      case None =>
        this.references(session) = collection.mutable.Map[String, ModelReference[_]]()
        this.references(session)
    }

    if (sessionRefs.contains(key)) {
      throw new Error("Model reference already exists");
    }

    sessionRefs(key) = reference
  }

  def get(session: SessionKey, key: String): Option[ModelReference[_]] = {
    this.references.get(session).flatMap { sr => sr.get(key) }
  }

  def getAll(): Set[ModelReference[_]] = {
    val buffer = ListBuffer[ModelReference[_]]()
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

  def remove(session: SessionKey, key: String): Option[ModelReference[_]] = {
    val result = this.get(session, key)
    if (result.isDefined) {
      references(session) -= key
    }
    result
  }

  def removeBySession(session: SessionKey): Unit = {
    references -= session
  }
}
