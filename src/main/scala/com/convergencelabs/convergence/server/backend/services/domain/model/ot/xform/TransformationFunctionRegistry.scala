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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.array._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.bool._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.date._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.number._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.obj._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.reference._
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.string._
import com.convergencelabs.convergence.server.model.domain.model.{IndexReferenceValues, ModelReferenceValues, RangeReferenceValues}

import scala.reflect.ClassTag

private[model] class TransformationFunctionRegistry {

  private[this] val otfs = new OTFMap()
  private[this] val rtfs = new RTFMap()

  // String Functions
  otfs.register[StringInsertOperation, StringInsertOperation](StringInsertInsertTF)
  otfs.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
  otfs.register[StringInsertOperation, StringSetOperation](StringInsertSetTF)

  otfs.register[StringRemoveOperation, StringInsertOperation](StringRemoveInsertTF)
  otfs.register[StringRemoveOperation, StringRemoveOperation](StringRemoveRemoveTF)
  otfs.register[StringRemoveOperation, StringSetOperation](StringRemoveSetTF)

  otfs.register[StringSetOperation, StringInsertOperation](StringSetInsertTF)
  otfs.register[StringSetOperation, StringRemoveOperation](StringSetRemoveTF)
  otfs.register[StringSetOperation, StringSetOperation](StringSetSetTF)

  // Object Functions
  otfs.register[ObjectAddPropertyOperation, ObjectAddPropertyOperation](ObjectAddPropertyAddPropertyTF)
  otfs.register[ObjectAddPropertyOperation, ObjectSetPropertyOperation](ObjectAddPropertySetPropertyTF)
  otfs.register[ObjectAddPropertyOperation, ObjectRemovePropertyOperation](ObjectAddPropertyRemovePropertyTF)
  otfs.register[ObjectAddPropertyOperation, ObjectSetOperation](ObjectAddPropertySetTF)

  otfs.register[ObjectRemovePropertyOperation, ObjectAddPropertyOperation](ObjectRemovePropertyAddPropertyTF)
  otfs.register[ObjectRemovePropertyOperation, ObjectSetPropertyOperation](ObjectRemovePropertySetPropertyTF)
  otfs.register[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation](ObjectRemovePropertyRemovePropertyTF)
  otfs.register[ObjectRemovePropertyOperation, ObjectSetOperation](ObjectRemovePropertySetTF)

  otfs.register[ObjectSetPropertyOperation, ObjectAddPropertyOperation](ObjectSetPropertyAddPropertyTF)
  otfs.register[ObjectSetPropertyOperation, ObjectSetPropertyOperation](ObjectSetPropertySetPropertyTF)
  otfs.register[ObjectSetPropertyOperation, ObjectRemovePropertyOperation](ObjectSetPropertyRemovePropertyTF)
  otfs.register[ObjectSetPropertyOperation, ObjectSetOperation](ObjectSetPropertySetTF)

  otfs.register[ObjectSetOperation, ObjectAddPropertyOperation](ObjectSetAddPropertyTF)
  otfs.register[ObjectSetOperation, ObjectSetPropertyOperation](ObjectSetSetPropertyTF)
  otfs.register[ObjectSetOperation, ObjectRemovePropertyOperation](ObjectSetRemovePropertyTF)
  otfs.register[ObjectSetOperation, ObjectSetOperation](ObjectSetSetTF)

  // Array Functions
  otfs.register[ArrayInsertOperation, ArrayInsertOperation](ArrayInsertInsertTF)
  otfs.register[ArrayInsertOperation, ArrayRemoveOperation](ArrayInsertRemoveTF)
  otfs.register[ArrayInsertOperation, ArrayReplaceOperation](ArrayInsertReplaceTF)
  otfs.register[ArrayInsertOperation, ArrayMoveOperation](ArrayInsertMoveTF)
  otfs.register[ArrayInsertOperation, ArraySetOperation](ArrayInsertSetTF)

  otfs.register[ArrayRemoveOperation, ArrayInsertOperation](ArrayRemoveInsertTF)
  otfs.register[ArrayRemoveOperation, ArrayRemoveOperation](ArrayRemoveRemoveTF)
  otfs.register[ArrayRemoveOperation, ArrayReplaceOperation](ArrayRemoveReplaceTF)
  otfs.register[ArrayRemoveOperation, ArrayMoveOperation](ArrayRemoveMoveTF)
  otfs.register[ArrayRemoveOperation, ArraySetOperation](ArrayRemoveSetTF)

  otfs.register[ArrayReplaceOperation, ArrayInsertOperation](ArrayReplaceInsertTF)
  otfs.register[ArrayReplaceOperation, ArrayRemoveOperation](ArrayReplaceRemoveTF)
  otfs.register[ArrayReplaceOperation, ArrayReplaceOperation](ArrayReplaceReplaceTF)
  otfs.register[ArrayReplaceOperation, ArrayMoveOperation](ArrayReplaceMoveTF)
  otfs.register[ArrayReplaceOperation, ArraySetOperation](ArrayReplaceSetTF)

  otfs.register[ArrayMoveOperation, ArrayInsertOperation](ArrayMoveInsertTF)
  otfs.register[ArrayMoveOperation, ArrayRemoveOperation](ArrayMoveRemoveTF)
  otfs.register[ArrayMoveOperation, ArrayReplaceOperation](ArrayMoveReplaceTF)
  otfs.register[ArrayMoveOperation, ArrayMoveOperation](ArrayMoveMoveTF)
  otfs.register[ArrayMoveOperation, ArraySetOperation](ArrayMoveSetTF)

  otfs.register[ArraySetOperation, ArrayInsertOperation](ArraySetInsertTF)
  otfs.register[ArraySetOperation, ArrayRemoveOperation](ArraySetRemoveTF)
  otfs.register[ArraySetOperation, ArrayReplaceOperation](ArraySetReplaceTF)
  otfs.register[ArraySetOperation, ArrayMoveOperation](ArraySetMoveTF)
  otfs.register[ArraySetOperation, ArraySetOperation](ArraySetSetTF)

  // Number Functions
  otfs.register[NumberAddOperation, NumberAddOperation](NumberAddAddTF)
  otfs.register[NumberAddOperation, NumberSetOperation](NumberAddSetTF)

  otfs.register[NumberSetOperation, NumberAddOperation](NumberSetAddTF)
  otfs.register[NumberSetOperation, NumberSetOperation](NumberSetSetTF)

  // Boolean Functions
  otfs.register[BooleanSetOperation, BooleanSetOperation](BooleanSetSetTF)

  // Date Functions
  otfs.register[DateSetOperation, DateSetOperation](DateSetSetTF)

  rtfs.register[StringInsertOperation, IndexReferenceValues](StringInsertIndexTF)
  rtfs.register[StringRemoveOperation, IndexReferenceValues](StringRemoveIndexTF)
  rtfs.register[StringSetOperation, IndexReferenceValues](StringSetIndexTF)

  rtfs.register[StringInsertOperation, RangeReferenceValues](StringInsertRangeTF)
  rtfs.register[StringRemoveOperation, RangeReferenceValues](StringRemoveRangeTF)
  rtfs.register[StringSetOperation, RangeReferenceValues](StringSetRangeTF)

  def getOperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation](s: S, c: C): Option[OperationTransformationFunction[S, C]] = {
    otfs.getOperationTransformationFunction(s, c)
  }

  def getReferenceTransformationFunction[O <: DiscreteOperation, V <: ModelReferenceValues](op: O, values: V): Option[ReferenceTransformationFunction[O, V]] = {
    rtfs.getReferenceTransformationFunction(op, values)
  }
}

private final case class RegistryKey[S, C](s: Class[S], c: Class[C])

private object RegistryKey {
  def of[S, C](implicit s: ClassTag[S], c: ClassTag[C]): RegistryKey[S, C] = {
    val sClass = s.runtimeClass.asInstanceOf[Class[S]]
    val cClass = c.runtimeClass.asInstanceOf[Class[C]]
    RegistryKey(sClass, cClass)
  }
}

private class OTFMap {
  private[this] var functions = Map[RegistryKey[_, _], OperationTransformationFunction[_, _]]()

  def register[S <: DiscreteOperation, C <: DiscreteOperation](otf: OperationTransformationFunction[S, C])(implicit s: ClassTag[S], c: ClassTag[C]): Unit = {
    val key = RegistryKey.of(s, c)
    if (functions.contains(key)) {
      throw new IllegalArgumentException(s"Transformation function already registered for ${key.s.getSimpleName}, ${key.c.getSimpleName}")
    } else {
      functions = functions + (key -> otf)
    }
  }

  def getOperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation](s: S, c: C): Option[OperationTransformationFunction[S, C]] = {
    val key = RegistryKey(s.getClass, c.getClass)
    functions.get(key).asInstanceOf[Option[OperationTransformationFunction[S, C]]]
  }
}

private class RTFMap {
  private[this] var functions = Map[(Class[_], Class[_]), ReferenceTransformationFunction[_, _]]()

  def register[O <: DiscreteOperation, V <: ModelReferenceValues](otf: ReferenceTransformationFunction[O, V])(implicit o: ClassTag[O], v: ClassTag[V]): Unit = {
    val oClass = o.runtimeClass.asInstanceOf[Class[O]]
    val vClass = v.runtimeClass.asInstanceOf[Class[V]]
    val key = (oClass, vClass)
    if (functions.contains(key)) {
      throw new IllegalArgumentException(s"Reference transformation function already registered for (${key._1.getSimpleName}, ${key._2.getSimpleName})")
    } else {
      functions = functions + (key -> otf)
    }
  }

  def getReferenceTransformationFunction[O <: DiscreteOperation, V <: ModelReferenceValues](op: O, values: V): Option[ReferenceTransformationFunction[O, V]] = {
    val oClass = op.getClass.asInstanceOf[Class[O]]
    val vClass = values.getClass.asInstanceOf[Class[V]]
    val key = (oClass, vClass)
    functions.get(key).asInstanceOf[Option[ReferenceTransformationFunction[O, V]]]
  }
}
