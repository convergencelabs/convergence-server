package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.ReferenceType
import com.convergencelabs.server.domain.model.ot.xform.reference._

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
  
  rtfs.register[StringInsertOperation](ReferenceType.Index, StringInsertIndexTF)
  rtfs.register[StringRemoveOperation](ReferenceType.Index, StringRemoveIndexTF)
  rtfs.register[StringSetOperation](ReferenceType.Index, StringSetIndexTF)
  
  rtfs.register[StringInsertOperation](ReferenceType.Range, StringInsertRangeTF)
  rtfs.register[StringRemoveOperation](ReferenceType.Range, StringRemoveRangeTF)
  rtfs.register[StringSetOperation](ReferenceType.Range, StringSetRangeTF)

  def getOperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation](s: S, c: C): Option[OperationTransformationFunction[S, C]] = {
    otfs.getOperationTransformationFunction(s, c)
  }

  def getReferenceTransformationFunction[O <: DiscreteOperation](op: O, referenceType: ReferenceType.Value): Option[ReferenceTransformationFunction[O]] = {
    rtfs.getReferenceTransformationFunction(op, referenceType)
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
  private[this] var otfs = Map[RegistryKey[_, _], OperationTransformationFunction[_, _]]()

  def register[S <: DiscreteOperation, C <: DiscreteOperation](otf: OperationTransformationFunction[S, C])(implicit s: ClassTag[S], c: ClassTag[C]): Unit = {
    val key = RegistryKey.of(s, c)
    if (otfs.get(key).isDefined) {
      throw new IllegalArgumentException(s"Transformation function already registered for ${key.s.getSimpleName}, ${key.c.getSimpleName}")
    } else {
      otfs = otfs + (key -> otf)
    }
  }

  def getOperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation](s: S, c: C): Option[OperationTransformationFunction[S, C]] = {
    val key = RegistryKey(s.getClass, c.getClass)
    otfs.get(key).asInstanceOf[Option[OperationTransformationFunction[S, C]]]
  }
}

private class RTFMap {
  private[this] var rtfs = Map[(Class[_], ReferenceType.Value), ReferenceTransformationFunction[_]]()

  def register[S <: DiscreteOperation](referenceType: ReferenceType.Value, otf: ReferenceTransformationFunction[S])(implicit s: ClassTag[S]): Unit = {
    val sClass = s.runtimeClass.asInstanceOf[Class[S]]
    val key = (sClass, referenceType)
    if (rtfs.get(key).isDefined) {
      throw new IllegalArgumentException(s"Reference transformation function already registered for ${key._1.getSimpleName}, ${key._2}")
    } else {
      rtfs = rtfs + (key -> otf)
    }
  }

  def getReferenceTransformationFunction[S <: DiscreteOperation](s: S, referenceType: ReferenceType.Value): Option[ReferenceTransformationFunction[S]] = {
    val sClass = s.getClass.asInstanceOf[Class[S]]
    val key = (sClass, referenceType)
    rtfs.get(key).asInstanceOf[Option[ReferenceTransformationFunction[S]]]
  }
}
