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

package com.convergencelabs.convergence.server.domain.model.ot.xform.obj

import com.convergencelabs.convergence.server.domain.model.ot._
import com.convergencelabs.convergence.server.domain.model.ot.xform.OperationTransformationFunction

private[ot] object ObjectSetPropertyAddPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectAddPropertyOperation): (ObjectSetPropertyOperation, ObjectAddPropertyOperation) = {
    if (s.property != c.property) {
      // O-TA-1
      (s, c)
    } else {
      // O-TA-2
      throw new IllegalArgumentException("Set property and add property can not target the same property")
    }
  }
}
