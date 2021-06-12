package com.convergencelabs.convergence.server.util

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.Charset

object  EntityIdSerializer {
  val Separator = "/"
}

/**
 * A helper base class that serializes and deserializes entity id's for the
 * Akka Cluster Sharding subsystem.
 *
 * @tparam T The object type that defines the unique id of the actor.
 */
abstract class EntityIdSerializer[T] {

  def serialize(id: T): String = {
    val parts = entityIdToParts(id)
    serializeParts(parts)
  }

  def deserialize(entityId: String): T = {
    val parts = deserializeParts(entityId)
    partsToEntityId(parts)
  }

  protected def serializeParts(parts: List[String]): String = {
    parts
      .map(URLEncoder.encode(_, Charset.defaultCharset()))
      .mkString(EntityIdSerializer.Separator)
  }

  protected def deserializeParts(encoded: String): List[String] = {
   encoded
     .split(EntityIdSerializer.Separator)
     .map(URLDecoder.decode(_, Charset.defaultCharset())).toList
  }

  protected def entityIdToParts(entityId: T): List[String]

  protected def partsToEntityId(parts: List[String]): T
}
