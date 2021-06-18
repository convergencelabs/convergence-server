package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

case class RemovePermissions(world: Set[String],
                             user: Map[DomainUserId, Set[String]],
                             group: Map[String, Set[String]])
