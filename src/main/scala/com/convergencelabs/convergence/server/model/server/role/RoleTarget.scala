/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.model.server.role

import com.convergencelabs.convergence.server.model.DomainId
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[DomainRoleTarget], name = "domain"),
    new JsonSubTypes.Type(value = classOf[NamespaceRoleTarget], name = "namespace"),
    new JsonSubTypes.Type(value = classOf[ServerRoleTarget], name = "server")
  )
)
sealed trait RoleTarget {
  def targetClass: Option[RoleTargetType.Value]
}

final case class DomainRoleTarget(domainId: DomainId) extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = Some(RoleTargetType.Domain)
}

final case class NamespaceRoleTarget(id: String) extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = Some(RoleTargetType.Namespace)
}

final case class ServerRoleTarget() extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = None
}