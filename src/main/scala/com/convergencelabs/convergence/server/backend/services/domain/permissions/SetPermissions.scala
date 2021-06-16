package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.{GroupPermissions, UserPermissions, WorldPermission}

case class SetPermissions(world: Option[Set[WorldPermission]],
                          user: Option[Set[UserPermissions]],
                          group: Option[Set[GroupPermissions]])
