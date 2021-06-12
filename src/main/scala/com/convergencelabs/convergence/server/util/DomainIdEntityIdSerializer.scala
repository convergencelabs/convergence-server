package com.convergencelabs.convergence.server.util

import com.convergencelabs.convergence.server.model.DomainId

/**
 * A help class to serialize a domain id to an entity id in the Akka
 * Cluster Sharding system.
 */
class DomainIdEntityIdSerializer extends EntityIdSerializer[DomainId] {

  override protected def entityIdToParts(domainId: DomainId): List[String] = List(
    domainId.namespace, domainId.domainId
  )

  override protected def partsToEntityId(parts: List[String]): DomainId =
    DomainId(parts.head, parts(1))
}
