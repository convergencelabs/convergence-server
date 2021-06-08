package com.convergencelabs.convergence.server.api.realtime

import scala.collection.mutable

/**
 * The ResourceManager class is a simple utility class that maps
 * application specific identifiers integer based resource id.
 *
 * @tparam T The application specific identifier type.
 */
class ResourceManager[T] {
  private var nextResource = 0
  private val resourceToId = mutable.Map[Int, T]()
  private val idToResource = mutable.Map[T, Int]()

  def getOrAssignResource(id: T): Int = {
    if (this.hasId(id)) {
      this.getResource(id).get
    } else {
      assignResource(id)
    }
  }

  def assignResource(id: T): Int = {
    val resource = claimNextResource()
    resourceToId += resource -> id
    idToResource += id -> resource
    resource
  }

  def releaseResource(resource: Int): Unit = {
    this.getId(resource).foreach { id =>
      resourceToId -= resource
      idToResource -= id
    }
  }

  def releaseResourceForId(id: T): Unit = {
    this.getResource(id).foreach { resource =>
      resourceToId -= resource
      idToResource -= id
    }
  }

  def hasResource(resource: Int): Boolean = {
    resourceToId.contains(resource)
  }

  def getResource(id: T): Option[Int] = {
    idToResource.get(id)
  }

  def hasId(id: T): Boolean = {
    idToResource.contains(id)
  }

  def getId(resource: Int): Option[T] = {
    this.resourceToId.get(resource)
  }

  private def claimNextResource(): Int = {
    val resource = nextResource
    nextResource += 1
    resource
  }
}
