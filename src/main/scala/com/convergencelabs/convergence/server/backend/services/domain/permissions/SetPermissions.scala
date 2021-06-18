package com.convergencelabs.convergence.server.backend.services.domain.permissions

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

case class SetPermissions(world: Option[Set[String]],
                          user: Option[Map[DomainUserId, Set[String]]],
                          group: Option[Map[String, Set[String]]])
