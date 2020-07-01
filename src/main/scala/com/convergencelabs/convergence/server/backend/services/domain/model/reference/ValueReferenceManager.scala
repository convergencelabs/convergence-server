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

package com.convergencelabs.convergence.server.backend.services.domain.model.reference

import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor.{SetReference, ShareReference}
import com.convergencelabs.convergence.server.backend.services.domain.model._
import com.convergencelabs.convergence.server.backend.services.domain.model.value.RealtimeValue
import com.convergencelabs.convergence.server.model.domain.model.{IndexReferenceValues, ModelReferenceValues, PropertyReferenceValues, RangeReferenceValues}

import scala.util.{Failure, Success, Try}

/**
 * Manages references for a RealtimeValue. Since the different [[RealtimeValue]]s
 * accept different kinds of references this class takes a set of classes
 * that it will accept. The classes must be subclasses of
 * [[ModelReferenceValues]].
 *
 * @param source            The source that this manager is managing
 *                          references for.
 * @param validValueClasses The valid value types that this instance accepts.
 */
private[model] class ValueReferenceManager(source: RealtimeValue, validValueClasses: List[Class[_ <: ModelReferenceValues]])
  extends AbstractReferenceManager[RealtimeValue](source) {

  override protected def processReferenceShared(event: ShareReference): Try[Unit] = {
    for {
      _ <- validateValueClass(event.values)
      reference <- event.values match {
        case RangeReferenceValues(ranges) =>
          Success(new RangeReference(this.source, event.session, event.key, ranges))
        case IndexReferenceValues(indices) =>
          Success(new IndexReference(this.source, event.session, event.key, indices))
        case PropertyReferenceValues(properties) =>
          Success(new PropertyReference(this.source, event.session, event.key, properties))
        case _ =>
          Failure(new IllegalArgumentException("Unexpected reference type"))
      }
    } yield {
      this.rm.put(reference)
      ()
    }
  }

  override protected def processReferenceSet(event: SetReference, reference: ModelReference[_, _]): Try[Unit] = {
    for {
      _ <- validateValueClass(event.values)
      _ <- (reference, event.values) match {
        case (reference: IndexReference, IndexReferenceValues(values)) =>
          reference.set(values)
          Success(())
        case (reference: RangeReference, RangeReferenceValues(values)) =>
          reference.set(values)
          Success(())
        case (reference: PropertyReference, PropertyReferenceValues(values)) =>
          reference.set(values)
          Success(())
        case _ =>
          Failure(new IllegalArgumentException("Unexpected reference type / value combination"))
      }
    } yield ()
  }

  override protected def validateSource(event: RealtimeModelActor.ModelReferenceEvent): Try[Unit] = {
    if (event.valueId.contains(this.source.id)) {
      Success(())
    } else {
      Failure(new IllegalArgumentException(s"The ModelReferenceEvent does not target this element(${source.id}): ${event.valueId}"))
    }
  }

  private[this] def validateValueClass(values: ModelReferenceValues): Try[Unit] = {
    if (this.validValueClasses.contains(values.getClass)) {
      Success(())
    } else {
      Failure(new IllegalArgumentException(s"Unsupported reference type for this element: ${values.getClass.getSimpleName}"))
    }
  }
}
