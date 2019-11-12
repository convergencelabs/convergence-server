/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
