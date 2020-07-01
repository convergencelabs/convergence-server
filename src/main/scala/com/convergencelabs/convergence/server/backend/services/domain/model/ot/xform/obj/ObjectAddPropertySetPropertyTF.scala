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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.obj

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.OperationTransformationFunction

private[ot] object ObjectAddPropertySetPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetPropertyOperation): (ObjectAddPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property != c.property) {
      // O-AT-1
      (s, c)
    } else {
      // O-AT-2
      throw new IllegalArgumentException("Add property and set property can not target the same property")
    }
  }
}
