package com.convergencelabs.server.domain.model.reference

import scala.collection.mutable.ListBuffer


class ReferenceMap {

  // stored by sessionId first, then key.
  private[this] val references = 
    collection.mutable.Map[String, collection.mutable.Map[String, ModelReference]]()

  def put(reference: ModelReference): Unit = {
    val sessionId: String = reference.sessionId
    val key: String = reference.key;

    val sessionRefs = this.references.get(sessionId) match {
      case Some(map) => map
      case None =>
        this.references(sessionId) = collection.mutable.Map[String, ModelReference]()
        this.references(sessionId)
    }

    if (sessionRefs.contains(key)) {
      throw new Error("Model reference already exists");
    }

    sessionRefs(key) = reference
  }

  def get(sessionId: String, key: String): Option[ModelReference] = {
    this.references.get(sessionId).flatMap { sr => sr.get(key) }
  }


  def removeAll(): Unit = {
    this.references.clear()
  }

  def remove(sessionId: String, key: String): Option[ModelReference] = {
    val result = this.get(sessionId, key)
    if (result.isDefined) {
      references(sessionId) -= key
    }
    result
  }

  def removeBySession(sessionId: String): Unit = {
    references -= sessionId
  }
}