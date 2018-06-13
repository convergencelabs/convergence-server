package com.convergencelabs.server.db.schema

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter

import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.metadata.function.OFunction
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.metadata.schema.OProperty
import com.orientechnologies.orient.core.metadata.sequence.OSequence

import grizzled.slf4j.Logging

object SchemaEqualityTester extends Logging {
  def assertEqual(db1: ODatabaseDocument, db2: ODatabaseDocument): Unit = {
    assertFunctionsEqual(db1, db2)
    assertSequencesEqual(db1, db2)
    assertClassesEqual(db1, db2)
    assertIndexesEqual(db1, db2)
  }

  private[this] def assertFunctionsEqual(db1: ODatabaseDocument, db2: ODatabaseDocument): Unit = {
    val functionLibrary1 = db1.getMetadata.getFunctionLibrary
    val functionLibrary2 = db2.getMetadata.getFunctionLibrary

    val functions = functionLibrary1.getFunctionNames.asScala.toSet
    val functions2 = functionLibrary2.getFunctionNames.asScala.toSet
    assume(functions == functions2, "Databases have different functions!")

    functions.foreach { function =>
      assertFunctionEqual(functionLibrary1.getFunction(function),
        functionLibrary2.getFunction(function))
    }
  }

  private[this] def assertFunctionEqual(function1: OFunction, function2: OFunction): Unit = {
    assume(function1.getName == function2.getName, "Function name is not the same!")
    assume(function1.getCode == function2.getCode, "Function code for ${function1.getName} is not the same!")
    assume(function1.getParameters.asScala.toSet == function2.getParameters.asScala.toSet, "Function parameter list for ${function1.getName} is not the same!")
    assume(function1.getLanguage == function2.getLanguage, "Function language for ${function1.getName} is not the same!")
    assume(function1.isIdempotent == function2.isIdempotent, "Function idempotence for ${function1.getName} is not the same!")
  }

  private[this] def assertIndexesEqual(db1: ODatabaseDocument, db2: ODatabaseDocument): Unit = {
    val indexManager1 = db1.getMetadata.getIndexManager
    val indexManager2 = db2.getMetadata.getIndexManager

    val indexes = indexManager1.getIndexes.asScala.toSet map { index: OIndex[_] => index.getName }
    val indexes2 = indexManager2.getIndexes.asScala.toSet map { index: OIndex[_] => index.getName }

    assume(indexes.subsetOf(indexes2), "Databases have different indexes!")
    assume(indexes2.subsetOf(indexes), "Databases have different indexes!")
    indexes.foreach { index =>
      assertIndexEqual(indexManager1.getIndex(index), indexManager2.getIndex(index))
    }
  }

  private[this] def assertSequencesEqual(db1: ODatabaseDocument, db2: ODatabaseDocument): Unit = {
    val sequenceLibrary1 = db1.getMetadata.getSequenceLibrary
    val sequenceLibrary2 = db2.getMetadata.getSequenceLibrary

    val sequences = sequenceLibrary1.getSequenceNames.asScala.toSet
    assume(sequences == sequenceLibrary2.getSequenceNames.asScala.toSet, "Databases have different functions!")
    sequences.foreach { sequence =>
      assertSequenceEqual(sequenceLibrary1.getSequence(sequence), sequenceLibrary2.getSequence(sequence))
    }
  }

  private[this] def assertIndexEqual(index1: OIndex[_], index2: OIndex[_]): Unit = {
    // TODO: Figure out how to compare metaData
    assume(index1.getName == index2.getName, "Index name is not the same!")
    assume(index1.getType == index2.getType, "Index type for ${index1.getName} is not the same!")
    assume(
        index1.getDefinition.getFields.asScala.toSet == index2.getDefinition.getFields.asScala.toSet, 
        "Index fields for ${index1.getName} is not the same!")
  }

  private[this] def assertSequenceEqual(seq1: OSequence, seq2: OSequence): Unit = {
    // TODO: Figure out how to compare cache size
    assume(seq1.getName == seq2.getName, "Sequence name is not the same!")
    assume(seq1.getSequenceType == seq2.getSequenceType, s"Sequence type for ${seq1.getName} is not the same!")
    assume(seq1.getDocument.field("start") == seq2.getDocument.field("start"), s"Sequence start for ${seq1.getName} is not the same!")
    assume(seq1.getDocument.field("incr") == seq2.getDocument.field("incr"), s"Sequence increment for ${seq1.getName} is not the same!")
  }

  private[this] def assertClassesEqual(db1: ODatabaseDocument, db2: ODatabaseDocument): Unit = {
    val schema1 = db1.getMetadata.getSchema
    val schema2 = db2.getMetadata.getSchema

    val classes1 = schema1.getClasses.asScala.toSet.map { (x: OClass) => x.getName }
    val classes2 = schema2.getClasses.asScala.toSet.map { (x: OClass) => x.getName }

    assume(classes1 == classes2, "Databases have different functions!")
    classes1.foreach { name =>
      assertClassEqual(schema1.getClass(name), schema2.getClass(name))
    }
  }

  private[this] def assertClassEqual(class1: OClass, class2: OClass): Unit = {
    val props1 = class1.properties.asScala.toSet.map { prop: OProperty => prop.getName }
    val props2 = class2.properties.asScala.toSet.map { prop: OProperty => prop.getName }

    assume(class1.getName == class2.getName, "Class name is not the same!")
    assume(class1.isAbstract() == class2.isAbstract(), s"Class type for ${class1.getName} is not the same!")
    assume(class1.getSuperClassesNames == class2.getSuperClassesNames, s"Class superclasses for ${class1.getName} is not the same!")
    assume(class2.getSuperClassesNames.containsAll(class1.getSuperClassesNames), s"Class superclasses for ${class1.getName} is not the same!")
    assume(props1 == props2, s"Class properties for ${class1.getName} is not the same! \n$props1 != \n$props2")
    props1.foreach { prop =>
      assertPropertyEqual(class1.getProperty(prop), class2.getProperty(prop))
    }
  }

  private[this] def assertPropertyEqual(prop1: OProperty, prop2: OProperty): Unit = {
    val customKeys1 = prop1.getCustomKeys.asScala.toSet

    assume(prop1.getName == prop2.getName, "Property name is not the same!")
      assume(prop1.getMin == prop2.getMin, s"Property min for ${prop1.getName} is not the same!")
      assume(prop1.getMax == prop2.getMax, s"Property max for ${prop1.getName} is not the same!")
      assume(prop1.isMandatory == prop2.isMandatory, s"Property mandatory flag for ${prop1.getName} is not the same!")
      assume(prop1.isReadonly == prop2.isReadonly, s"Property readOnly flag for ${prop1.getName} is not the same!")
      assume(prop1.isNotNull == prop2.isNotNull, s"Property notNull flag for ${prop1.getName} is not the same!")
      assume(prop1.getDefaultValue == prop2.getDefaultValue, s"Property defaultValue for ${prop1.getName} is not the same!")
      assume(prop1.getRegexp == prop2.getRegexp, s"Property regexp for ${prop1.getName} is not the same!")
      assume(customKeys1 == prop2.getCustomKeys.asScala.toSet, s"Property custom keys for ${prop1.getName} is not the same!")
      assume(customKeys1.forall { key => prop1.getCustom(key) == prop2.getCustom(key) }, s"Property custom keys for ${prop1.getName} is not the same!")
      assume(prop1.getCollate == prop2.getCollate, s"Property collate type for ${prop1.getName} is not the same!")
      assume(prop1.getType == prop2.getType, s"Property type for ${prop1.getName} is not the same!")
      assume(prop1.getLinkedType == prop2.getLinkedType, s"Property linked type for ${prop1.getName} is not the same!")
      
      assume((Option(prop1.getLinkedClass) map { lc: OClass => lc.getName }) == (Option(prop2.getLinkedClass) map { lc: OClass => lc.getName }), 
          s"Property linked class for ${prop1.getName} is not the same!")
  }
}