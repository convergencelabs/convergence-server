package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

final case class SetPermissions(world: Option[Set[String]],
                          user: Option[SetUserPermissions],
                          group: Option[SetGroupPermissions])

final case class SetUserPermissions(permissions: Map[DomainUserId, Set[String]], replace: Boolean)

final case class SetGroupPermissions(permissions: Map[String, Set[String]], replace: Boolean)