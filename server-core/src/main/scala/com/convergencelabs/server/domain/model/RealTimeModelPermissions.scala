package com.convergencelabs.server.domain.model

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.DomainUserId

case class RealTimeModelPermissions(
    overrideCollection: Boolean,
    collectionWorld: CollectionPermissions,
    collectionUsers: Map[DomainUserId, CollectionPermissions],
    modelWorld: ModelPermissions,
    modelUsers: Map[DomainUserId, ModelPermissions]) {

  def resolveSessionPermissions(userId: DomainUserId): ModelPermissions = {
    if (userId.isConvergence) {
      ModelPermissions(true, true, true, true)
    } else {
      if (overrideCollection) {
        modelUsers.getOrElse(userId, modelWorld)
      } else {
        val CollectionPermissions(create, read, write, remove, manage) = collectionUsers.getOrElse(userId, collectionWorld)
        ModelPermissions(read, write, remove, manage)
      }
    }
  }
}
