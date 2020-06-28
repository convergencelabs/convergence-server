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

package com.convergencelabs.convergence.server.domain.model.reference

import com.convergencelabs.convergence.server.domain.DomainUserSessionId
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor.{SetReference, ShareReference}
import com.convergencelabs.convergence.server.domain.model._

import scala.util.Try


class ReferenceManager(source: RealTimeValue,
                       validValueClasses: List[Class[_]])
  extends AbstractReferenceManager[RealTimeValue](source, validValueClasses) {

  override protected def processReferenceShared(event: ShareReference, session: DomainUserSessionId): Try[Unit] = Try {
    // FIXME set value.
    val reference = event.values match {
      case RangeReferenceValues(ranges) =>
        new RangeReference(this.source, session, event.key)
      case IndexReferenceValues(indices) =>
        new IndexReference(this.source, session, event.key)
      case PropertyReferenceValues(properties) =>
        new PropertyReference(this.source, session, event.key)
      case _ =>
        throw new IllegalArgumentException("Unexpected reference type")
    }

    this.rm.put(reference)
    ()
  }

  override protected def processReferenceSet(event: SetReference, reference: ModelReference[_, _], session: DomainUserSessionId): Try[Unit] = Try {
    (reference, event.values) match {
      case (reference: IndexReference, IndexReferenceValues(values)) =>
        reference.set(values)
      case (reference: RangeReference, RangeReferenceValues(values)) =>
        reference.set(values)
      case (reference: PropertyReference, PropertyReferenceValues(values)) =>
        reference.set(values)
      case _ =>
        throw new IllegalArgumentException("Unexpected reference type / value combination")
    }
  }
}
