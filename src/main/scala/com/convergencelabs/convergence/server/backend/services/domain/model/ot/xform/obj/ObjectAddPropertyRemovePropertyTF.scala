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

private[ot] object ObjectAddPropertyRemovePropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectRemovePropertyOperation): (ObjectAddPropertyOperation, ObjectRemovePropertyOperation) = {
    if (s.property != c.property) {
      // O-AR-1
      (s, c)
    } else {
      // O-AR-2
      throw new IllegalArgumentException("Add property and remove property can not target the same property")
    }
  }
}
