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
                       validTypes: List[ReferenceType.Value])
  extends AbstractReferenceManager[RealTimeValue](source, validTypes) {

  override protected def processReferenceShared(event: ShareReference, session: DomainUserSessionId): Try[Unit] = Try {
    val reference = event.referenceType match {
      case ReferenceType.Index =>
        new IndexReference(this.source, session, event.key)
      case ReferenceType.Range =>
        new RangeReference(this.source, session, event.key)
      case ReferenceType.Property =>
        new PropertyReference(this.source, session, event.key)
      case _ =>
        throw new IllegalArgumentException("Unexpected reference type")
    }

    this.rm.put(reference)
    ()
  }

  override protected def processReferenceSet(event: SetReference, reference: ModelReference[_], session: DomainUserSessionId): Try[Unit] = Try {
    reference match {
      case reference: IndexReference =>
        reference.set(event.values.asInstanceOf[List[Int]])
      case reference: RangeReference =>
        reference.set(event.values.asInstanceOf[List[(Int, Int)]])
      case reference: PropertyReference =>
        reference.set(event.values.asInstanceOf[List[String]])
      case _ =>
        throw new IllegalArgumentException("Unexpected reference type")
    }
  }
}
