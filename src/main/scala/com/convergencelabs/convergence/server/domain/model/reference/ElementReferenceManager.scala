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
import com.convergencelabs.convergence.server.domain.model.{ElementReferenceValues, RealTimeModel}

import scala.util.Try

object ElementReferenceManager {
  val ReferenceDoesNotExist = "Reference does not exist"
}

class ElementReferenceManager(source: RealTimeModel,
                              validValueClasses: List[Class[_]])
  extends AbstractReferenceManager[RealTimeModel](source, validValueClasses) {


  override protected def processReferenceShared(event: ShareReference, session: DomainUserSessionId): Try[Unit] = Try {
    val reference = event.values match {
      case ElementReferenceValues(elementIds) =>
        new ElementReference(source, session, event.key)
      case _ =>
        throw new IllegalArgumentException("Unexpected reference type")
    }

    this.rm.put(reference)
  }

  override protected def processReferenceSet(event: SetReference, reference: ModelReference[_, _], session: DomainUserSessionId): Try[Unit] = Try {
    (reference, event.values) match {
      case (reference: ElementReference, ElementReferenceValues(elementIds)) =>
        elementIds filter source.idToValue.contains

        for (vid <- elementIds) {
          source.idToValue(vid).addDetachListener(reference.handleElementDetached)
        }

        reference.set(elementIds)
      case _ =>
        throw new IllegalArgumentException("Unexpected reference / value combination")
    }
  }
}
