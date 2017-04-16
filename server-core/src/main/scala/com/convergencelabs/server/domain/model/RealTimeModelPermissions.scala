package com.convergencelabs.server.domain.model

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissions

case class RealTimeModelPermissions(
    overrideCollection: Boolean,
    collectionWorld: CollectionPermissions,
    collectionUsers: Map[String, CollectionPermissions],
    modelWorld: ModelPermissions,
    modelUsers: Map[String, ModelPermissions]) {

  def resolveSessionPermissions(sk: SessionKey): ModelPermissions = {
    if (sk.admin) {
      ModelPermissions(true, true, true, true)
    } else {
      if (overrideCollection) {
        modelUsers.getOrElse(sk.uid, modelWorld)
      } else {
        val CollectionPermissions(create, read, write, remove, manage) = collectionUsers.getOrElse(sk.uid, collectionWorld)
        ModelPermissions(read, write, remove, manage)
      }
    }
  }
}