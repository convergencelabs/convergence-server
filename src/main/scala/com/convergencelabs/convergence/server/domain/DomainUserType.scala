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

package com.convergencelabs.convergence.server.domain

import com.fasterxml.jackson.core.`type`.TypeReference

object DomainUserType extends Enumeration {
  type DomainUserType = Value
  val Normal: DomainUserType = Value("normal")
  val Anonymous: DomainUserType = Value("anonymous")
  val Convergence: DomainUserType = Value("convergence")

  def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())
}

class DomainUserTypeReference extends TypeReference[DomainUserType.type]