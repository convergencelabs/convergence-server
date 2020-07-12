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

package com.convergencelabs.convergence.server.backend.db.schema

import java.util

import com.convergencelabs.convergence.server.backend.db.schema.delta._
import com.convergencelabs.convergence.server.util.SafeTry
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.function.OFunction
import com.orientechnologies.orient.core.metadata.schema.{OClass, OProperty, OType}
import com.orientechnologies.orient.core.metadata.sequence.OSequence
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.jdk.CollectionConverters._
import scala.util.Try

private[schema] final class DeltaProcessor(delta: Delta, db: ODatabaseDocument) extends Logging {

  private[this] var deferredLinkedProperties = Map[OProperty, String]()

  def applyDelta(): Try[Unit] = SafeTry {
    db.activateOnCurrentThread()
    delta.actions foreach (change => applyChange(change))
    processDeferredLinkedClasses()
    db.getMetadata.reload()
    ()
  }

  private[this] def applyChange(change: DeltaAction): Unit = {
    change match {
      case createClass: CreateClass => applyCreateClass(createClass)
      case alterClass: AlterClass => applyAlterClass(alterClass)
      case dropClass: DropClass => applyDropClass(dropClass)
      case addProperty: AddProperty => applyAddProperty(addProperty)
      case alterProperty: AlterProperty => applyAlterProperty(alterProperty)
      case dropProperty: DropProperty => applyDropProperty(dropProperty)
      case createIndex: CreateIndex => applyCreateIndex(createIndex)
      case dropIndex: DropIndex => applyDropIndex(dropIndex)
      case createSequence: CreateSequence => applyCreateSequence(createSequence)
      case dropSequence: DropSequence => applyDropSequence(dropSequence)
      case runSQLCommand: RunSqlCommand => applyRunSQLCommand(runSQLCommand)
      case createFunction: CreateFunction => applyCreateFunction(createFunction)
      case alterFunction: AlterFunction => applyAlterFunction(alterFunction)
      case dropFunction: DropFunction => applyDropFunction(dropFunction)
    }
  }

  private[this] def applyCreateClass(createClass: CreateClass): Unit = {
    val CreateClass(name, superclass, isAbstract, properties) = createClass
    val sClass = superclass.map(db.getMetadata.getSchema.getClass(_))

    val newClass = (isAbstract, sClass) match {
      case (Some(true), Some(oClass)) => db.getMetadata.getSchema.createAbstractClass(name, oClass)
      case (_, Some(oClass)) => db.getMetadata.getSchema.createClass(name, oClass)
      case (Some(true), None) => db.getMetadata.getSchema.createAbstractClass(name)
      case (_, None) => db.getMetadata.getSchema.createClass(name)
    }

    properties foreach (addProperty(newClass, _))
  }

  private[this] def applyAlterClass(alterClass: AlterClass): Unit = {
    val AlterClass(name, newName, superclass) = alterClass
    val oClass: OClass = db.getMetadata.getSchema.getClass(name)
    newName.foreach(oClass.setName)
    superclass.foreach { supName =>
      val superClass: OClass = db.getMetadata.getSchema.getClass(supName)
      val arrayList = new util.ArrayList[OClass]()
      arrayList.add(superClass)
      oClass.setSuperClasses(arrayList)
    }
  }

  private[this] def applyDropClass(dropClass: DropClass): Unit = {
    val DropClass(name) = dropClass
    db.getMetadata.getSchema.dropClass(name)
  }

  private[this] def applyAddProperty(addProperty: AddProperty): Unit = {
    val AddProperty(className, property) = addProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    this.addProperty(oClass, property)
  }

  private[this] def addProperty(oClass: OClass, property: Property): Unit = {
    val Property(name, orientType, linkedType, linkedClass, constraints) = property
    val oProp: OProperty = (linkedType, linkedClass) match {
      case (None, None) =>
        // not linked
        oClass.createProperty(name, toOType(orientType))
      case (Some(typeName), None) =>
        // orientType
        oClass.createProperty(name, toOType(orientType), toOType(typeName))
      case (None, Some(className)) =>
        Option(db.getMetadata.getSchema.getClass(className)) match {
          case Some(c) =>
            // Already defined, create it now with the link
            oClass.createProperty(name, toOType(orientType), c)
          case None =>
            // not defined yet, defer the linking of the property and just create the property
            // without the link now.
            val prop = oClass.createProperty(name, toOType(orientType))
            this.deferredLinkedProperties += (prop -> className)
            prop
        }
      case (Some(_), Some(_)) =>
        throw new IllegalArgumentException("Can not specify both a linked class and linked type")
    }
    constraints.foreach(setConstraints(oProp, _))
  }

  private[this] def applyAlterProperty(alterProperty: AlterProperty): Unit = {
    val AlterProperty(className, name, PropertyOptions(newName, orientType, linkedType, linkedClass, constraints)) = alterProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    if (Option(oClass).isEmpty) {
      throw new IllegalArgumentException(s"Can not alter property on class '$className' that does not exists.")
    }

    val oProp: OProperty = oClass.getProperty(name)
    if (Option(oProp).isEmpty) {
      throw new IllegalArgumentException(s"Property '$name' does not exist on class '$className'.")
    }

    newName.foreach(oProp.setName)
    orientType.foreach(oType => oProp.setType(toOType(oType)))

    (linkedType, linkedClass) match {
      case (None, None) =>
        // not linked
        if (oProp.getLinkedClass != null) {
          oProp.setLinkedClass(null)
        }

        if (oProp.getLinkedType != null) {
          oProp.setLinkedType(null)
        }
      case (Some(typeName), None) =>
        // linked orientType
        if (oProp.getLinkedClass != null) {
          oProp.setLinkedClass(null)
        }
        oProp.setLinkedType(toOType(typeName))
      case (None, Some(className)) =>
        // linked class
        if (oProp.getLinkedType != null) {
          oProp.setLinkedType(null)
        }
        Option(db.getMetadata.getSchema.getClass(className)) match {
          case Some(c) =>
            // Already defined, create it now with the link
            oProp.setLinkedClass(c)
          case None =>
            // not defined yet, defer the linking of the property and just create the property
            // without the link now.
            this.deferredLinkedProperties += (oProp -> className)
        }
      case (Some(_), Some(_)) =>
        throw new IllegalArgumentException("Can not specify both a linked class and linked type")
    }

    constraints.foreach(setConstraints(oProp, _))
  }

  private[this] def setConstraints(oProp: OProperty, constraints: Constraints): Unit = {
    constraints.min.foreach(oProp.setMin)
    constraints.max.foreach(oProp.setMax)
    constraints.mandatory.foreach(oProp.setMandatory)
    constraints.readOnly.foreach(oProp.setReadonly)
    constraints.notNull.foreach(oProp.setNotNull)
    constraints.regex.foreach(oProp.setRegexp)
    constraints.collate.foreach(oProp.setCollate)
    constraints.custom.foreach(customProp => oProp.setCustom(customProp.name, customProp.value))
    constraints.default.foreach(oProp.setDefaultValue)
  }

  private[this] def applyDropProperty(dropProperty: DropProperty): Unit = {
    val DropProperty(className, name) = dropProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    oClass.dropProperty(name)
  }

  private[this] def applyCreateIndex(createIndex: CreateIndex): Unit = {
    val CreateIndex(className, name, indexType, properties, metaData) = createIndex
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)

    val metaDataDoc = metaData match {
      case Some(map) =>
        new ODocument().fromMap(map.asJava)
      case None =>
        new ODocument()
    }

    oClass.createIndex(name, toOIndexType(indexType).toString, null, metaDataDoc, properties: _*)
  }

  private[this] def applyDropIndex(dropIndex: DropIndex): Unit = {
    val DropIndex(name) = dropIndex
    db.getMetadata.getIndexManager.dropIndex(name)
  }

  private[this] def applyCreateSequence(createSequence: CreateSequence): Unit = {
    val CreateSequence(name, sType, start, increment, cacheSize) = createSequence
    val sequenceLibrary = db.getMetadata.getSequenceLibrary

    val params = new OSequence.CreateParams()
    params.setStart(0L).setIncrement(1)
    start.foreach(params.setStart(_))
    increment.foreach(params.setIncrement(_))
    cacheSize.foreach(params.setCacheSize(_))

    sequenceLibrary.createSequence(name, sType match {
      case SequenceType.Cached => SEQUENCE_TYPE.CACHED
      case SequenceType.Ordered => SEQUENCE_TYPE.ORDERED
    }, params)
  }

  private[this] def applyDropSequence(dropSequence: DropSequence): Unit = {
    val DropSequence(name) = dropSequence
    db.getMetadata.getSequenceLibrary.dropSequence(name)
  }

  private[this] def applyRunSQLCommand(runSQLCommand: RunSqlCommand): Unit = {
    val RunSqlCommand(command) = runSQLCommand
    db.execute("sql", command, new java.util.HashMap())
  }

  private[this] def applyCreateFunction(createFunction: CreateFunction): Unit = {
    val CreateFunction(name, code, parameters, language, idempotent) = createFunction
    val function: OFunction = db.getMetadata.getFunctionLibrary.createFunction(name)

    function.setCode(code)
    function.setParameters(parameters.asJava)
    language.foreach(function.setLanguage)
    idempotent.foreach(function.setIdempotent)
    function.save()
  }

  private[this] def applyAlterFunction(alterFunction: AlterFunction): Unit = {
    val AlterFunction(name, newName, code, parameters, language, idempotent) = alterFunction
    val function: OFunction = db.getMetadata.getFunctionLibrary.getFunction(name)

    newName.foreach(function.setName)
    code.foreach(function.setCode)
    parameters.foreach(params => function.setParameters(params.asJava))
    language.foreach(function.setLanguage)
    idempotent.foreach(function.setIdempotent)
    function.save()
  }

  private[this] def applyDropFunction(dropFunction: DropFunction): Unit = {
    val DropFunction(name) = dropFunction
    db.getMetadata.getFunctionLibrary.dropFunction(name)
  }

  private[this] def toOType(orientType: OrientType.Value): OType = {
    orientType match {
      case OrientType.Boolean => OType.BOOLEAN
      case OrientType.Integer => OType.INTEGER
      case OrientType.Short => OType.SHORT
      case OrientType.Long => OType.LONG
      case OrientType.Float => OType.FLOAT
      case OrientType.Double => OType.DOUBLE
      case OrientType.DateTime => OType.DATETIME
      case OrientType.String => OType.STRING
      case OrientType.Binary => OType.BINARY
      case OrientType.Embedded => OType.EMBEDDED
      case OrientType.EmbeddedList => OType.EMBEDDEDLIST
      case OrientType.EmbeddedSet => OType.EMBEDDEDSET
      case OrientType.EmbeddedMap => OType.EMBEDDEDMAP
      case OrientType.Link => OType.LINK
      case OrientType.LinkList => OType.LINKLIST
      case OrientType.LinkSet => OType.LINKSET
      case OrientType.LinkMap => OType.LINKMAP
      case OrientType.Byte => OType.BYTE
      case OrientType.Transient => OType.TRANSIENT
      case OrientType.Date => OType.DATE
      case OrientType.Custom => OType.CUSTOM
      case OrientType.Decimal => OType.DECIMAL
      case OrientType.LinkBag => OType.LINKBAG
      case OrientType.Any => OType.ANY
    }
  }

  private[this] def toOIndexType(indexType: IndexType.Value): OClass.INDEX_TYPE = {
    indexType match {
      case IndexType.Unique => OClass.INDEX_TYPE.UNIQUE
      case IndexType.NotUnique => OClass.INDEX_TYPE.NOTUNIQUE
      case IndexType.FullText => OClass.INDEX_TYPE.FULLTEXT
      case IndexType.Dictionary => OClass.INDEX_TYPE.DICTIONARY
      case IndexType.Proxy => OClass.INDEX_TYPE.PROXY
      case IndexType.UniqueHashIndex => OClass.INDEX_TYPE.UNIQUE_HASH_INDEX
      case IndexType.NotUniqueHashIndex => OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX
      case IndexType.DictionaryHashIndex => OClass.INDEX_TYPE.DICTIONARY_HASH_INDEX
      case IndexType.Spatial => OClass.INDEX_TYPE.SPATIAL
    }
  }

  private[this] def processDeferredLinkedClasses(): Unit = {
    deferredLinkedProperties map {
      case (prop, className) =>
        Option(db.getMetadata.getSchema.getClass(className)) match {
          case Some(linkedClass) =>
            prop.setLinkedClass(linkedClass)
          case None =>
            throw new IllegalStateException(
              s"Could not set linked class because the class does not exist: $className")
        }
    }
  }
}
