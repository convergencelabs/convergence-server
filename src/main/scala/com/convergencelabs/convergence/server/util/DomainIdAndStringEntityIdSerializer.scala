package com.convergencelabs.convergence.server.util

import com.convergencelabs.convergence.server.model.DomainId

/**
 * A help class to serialize a DomainId plus a unique string identifier
 * within the domain to an entity id in the Akka Cluster Sharding system.
 */
class DomainIdAndStringEntityIdSerializer extends EntityIdSerializer[(DomainId, String)] {

  override protected def entityIdToParts(entityId: (DomainId, String)): List[String] = List(
    entityId._1.namespace, entityId._1.domainId, entityId._2
  )

  override protected def partsToEntityId(parts: List[String]): (DomainId, String) =
    (DomainId(parts.head, parts(1)), parts(2))
}
