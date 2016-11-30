package com.convergencelabs.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import scala.collection.JavaConversions._
import scala.util.Try
import scala.util.Failure
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.metadata.schema.OProperty
import com.orientechnologies.orient.core.index.OIndex
import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.metadata.function.OFunction
import com.orientechnologies.orient.core.metadata.sequence.OSequence

object SchemaEqualityTester extends Logging {
  def isEqual(db1: ODatabaseDocumentTx, db2: ODatabaseDocumentTx): Boolean = {
    areFunctionsEqual(db1, db2) &&
      areSequencesEqual(db1, db2) &&
      areClassesEqual(db1, db2) &&
      areIndexesEqual(db1, db2)
  }

  private[this] def areFunctionsEqual(db1: ODatabaseDocumentTx, db2: ODatabaseDocumentTx): Boolean = {
    val functionLibrary1 = db1.getMetadata.getFunctionLibrary
    val functionLibrary2 = db2.getMetadata.getFunctionLibrary

    val functions = functionLibrary1.getFunctionNames.toSet
    functions == functionLibrary2.getFunctionNames.toSet &&
      functions.forall { function =>
        isFunctionEqual(functionLibrary1.getFunction(function),
          functionLibrary2.getFunction(function))
      }
  }

  private[this] def isFunctionEqual(function1: OFunction, function2: OFunction): Boolean = {
    function1.getName == function2.getName &&
      function1.getCode == function2.getCode &&
      function1.getParameters.toSet == function2.getParameters.toSet &&
      function1.getLanguage == function2.getLanguage &&
      function1.isIdempotent == function2.isIdempotent
  }

  private[this] def areIndexesEqual(db1: ODatabaseDocumentTx, db2: ODatabaseDocumentTx): Boolean = {
    val indexManager1 = db1.getMetadata.getIndexManager
    val indexManager2 = db2.getMetadata.getIndexManager

    val indexes = indexManager1.getIndexes.toList map { _.getName }

    indexes == (indexManager2.getIndexes.toList map { _.getName }) &&
      indexes.forall { index =>
        isIndexEqual(indexManager1.getIndex(index), indexManager2.getIndex(index))
      }
  }

  private[this] def isIndexEqual(index1: OIndex[_], index2: OIndex[_]): Boolean = {
    // TODO: Figure out how to compare metaData
    index1.getName == index2.getName &&
      index1.getType == index2.getType &&
      index1.getDefinition.getFields.toSet == index2.getDefinition.getFields.toSet
  }

  private[this] def areSequencesEqual(db1: ODatabaseDocumentTx, db2: ODatabaseDocumentTx): Boolean = {
    val sequenceLibrary1 = db1.getMetadata.getSequenceLibrary
    val sequenceLibrary2 = db2.getMetadata.getSequenceLibrary

    val sequences = sequenceLibrary1.getSequenceNames.toSet
    sequences == sequenceLibrary2.getSequenceNames.toSet &&
      sequences.forall { sequence =>
        isSequenceEqual(sequenceLibrary1.getSequence(sequence), sequenceLibrary2.getSequence(sequence))
      }
  }

  private[this] def isSequenceEqual(seq1: OSequence, seq2: OSequence): Boolean = {
    // TODO: Figure out how to compare cache size
    seq1.getName == seq2.getName &&
    seq1.getSequenceType == seq2.getSequenceType &&
    seq1.getDocument.field("start") == seq2.getDocument.field("start") &&
    seq1.getDocument.field("incr") == seq2.getDocument.field("incr")
  }

  private[this] def areClassesEqual(db1: ODatabaseDocumentTx, db2: ODatabaseDocumentTx): Boolean = {
    val schema1 = db1.getMetadata.getSchema
    val schema2 = db2.getMetadata.getSchema

    val classes1 = schema1.getClasses.map { _.getName }.toSet
    val classes2 = schema2.getClasses.map { _.getName }.toSet

    classes1 == classes2 &&
      classes1.forall { name =>
        isClassEqual(schema1.getClass(name), schema2.getClass(name))
      }
  }

  private[this] def isClassEqual(class1: OClass, class2: OClass): Boolean = {
    val props1 = class1.properties.toSet.map { prop: OProperty => prop.getName }
    val props2 = class2.properties.toSet.map { prop: OProperty => prop.getName }

    class1.getName == class2.getName &&
      class1.isAbstract() == class2.isAbstract() &&
      class1.getSuperClassesNames == class2.getSuperClassesNames &&
      class2.getSuperClassesNames.containsAll(class1.getSuperClassesNames) &&
      props1 == props2 &&
      props1.forall { prop =>
        isPropertyEqual(class1.getProperty(prop), class2.getProperty(prop))
      }
  }

  private[this] def isPropertyEqual(prop1: OProperty, prop2: OProperty): Boolean = {
    val customKeys1 = prop1.getCustomKeys.toSet

    prop1.getName == prop2.getName &&
      prop1.getMin == prop2.getMin &&
      prop1.getMax == prop2.getMax &&
      prop1.isMandatory == prop2.isMandatory &&
      prop1.isReadonly == prop2.isReadonly &&
      prop1.isNotNull == prop2.isNotNull &&
      prop1.getDefaultValue == prop2.getDefaultValue &&
      prop1.getRegexp == prop2.getRegexp &&
      customKeys1 == prop2.getCustomKeys.toSet &&
      customKeys1.forall { key => prop1.getCustom(key) == prop2.getCustom(key) } &&
      prop1.getCollate == prop2.getCollate &&
      prop1.getType == prop2.getType &&
      prop1.getLinkedType == prop2.getLinkedType &&
      (Option(prop1.getLinkedClass) map { lc: OClass => lc.getName }) == (Option(prop2.getLinkedClass) map { lc: OClass => lc.getName })
  }
}