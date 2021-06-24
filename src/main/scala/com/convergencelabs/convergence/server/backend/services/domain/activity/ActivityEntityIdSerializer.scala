package com.convergencelabs.convergence.server.backend.services.domain.activity

import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.ActivityId
import com.convergencelabs.convergence.server.util.EntityIdSerializer

/**
 * A help class to serialize a DomainId and an activity id
 * within the domain to an entity id in the Akka Cluster Sharding system.
 */
class ActivityEntityIdSerializer extends EntityIdSerializer[(DomainId, ActivityId)] {

  override protected def entityIdToParts(entityId: (DomainId, ActivityId)): List[String] = List(
    entityId._1.namespace, entityId._1.domainId, entityId._2.activityType, entityId._2.id
  )

  override protected def partsToEntityId(parts: List[String]): (DomainId, ActivityId) =
    (DomainId(parts.head, parts(1)), ActivityId(parts(2), parts(3)))
}
