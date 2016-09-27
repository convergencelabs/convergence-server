package com.convergencelabs.server.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OProperty
import com.orientechnologies.orient.core.metadata.schema.OClass
import java.util.ArrayList
import com.orientechnologies.common.listener.OProgressListener
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.metadata.sequence.OSequence
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.sql.OCommandSQL

object OrientSchemaProcessor {
  def main(args: Array[String]): Unit = {
      val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())
      db.create()
      val processor = new OrientSchemaProcessor(db)
      
      val delta = Delta(1, "Test", List(CreateClass("myClass", None, List())))
      processor.applyDelta(delta)
      println(db.getMetadata.getSchema.existsClass("myClass"))
  }
}

class OrientSchemaProcessor(db: ODatabaseDocumentTx) {

  def applyDelta(delta: Delta): Unit = {
    delta.changes foreach { change => applyChange(change) }
  }

  private def applyChange(change: Change): Unit = {
    change match {
      case createClass: CreateClass       => applyCreateClass(createClass)
      case alterClass: AlterClass         => applyAlterClass(alterClass)
      case dropClass: DropClass           => applyDropClass(dropClass)
      case addProperty: AddProperty       => applyAddProperty(addProperty)
      case alterProperty: AlterProperty   => applyAlterProperty(alterProperty)
      case dropProperty: DropProperty     => applyDropProperty(dropProperty)
      case createIndex: CreateIndex       => applyCreateIndex(createIndex)
      case dropIndex: DropIndex           => applyDropIndex(dropIndex)
      case createSequence: CreateSequence => applyCreateSequence(createSequence)
      case dropSequence: DropSequence     => applyDropSequence(dropSequence)
      case runSQLCommand: RunSQLCommand   => applyRunSQLCommand(runSQLCommand)
    }
  }

  private def applyCreateClass(createClass: CreateClass): Unit = {
    val CreateClass(name, superclass, properties) = createClass
    val newClass = superclass match {
      case Some(className) => {
        val oClass: OClass = db.getMetadata.getSchema.getClass(className)
        db.getMetadata.getSchema.createClass(name, oClass)
      }
      case None => db.getMetadata.getSchema.createClass(name)
    }

    properties foreach { addProperty(newClass, _) }
  }

  private def applyAlterClass(alterClass: AlterClass): Unit = {
    val AlterClass(name, newName, superclass) = alterClass
    val oClass: OClass = db.getMetadata.getSchema.getClass(name)
    newName.foreach { oClass.setName(_) }
    superclass.foreach { supName =>
      val superClass: OClass = db.getMetadata.getSchema.getClass(supName)
      val arrayList = new ArrayList[OClass]()
      arrayList.add(superClass)
      oClass.setSuperClasses(arrayList)
    }
  }

  private def applyDropClass(dropClass: DropClass): Unit = {
    val DropClass(name) = dropClass
    db.getMetadata.getSchema.dropClass(name)

  }

  private def applyAddProperty(addProperty: AddProperty): Unit = {
    val AddProperty(className, property) = addProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    this.addProperty(oClass, property)
  }

  private def addProperty(oClass: OClass, property: Property): Unit = {
    val Property(name, orientType, typeClass, constraints) = property
    val oProp: OProperty = typeClass match {
      case Some(className) => oClass.createProperty(name, toOType(orientType), db.getMetadata.getSchema.getClass(className))
      case None            => oClass.createProperty(name, toOType(orientType))
    }
    constraints.foreach { setConstraints(oProp, _) }
  }

  private def applyAlterProperty(alterProperty: AlterProperty): Unit = {
    val AlterProperty(className, name, PropertyOptions(newName, orientType, typeClass, constraints)) = alterProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    val oProp: OProperty = oClass.getProperty(name)
    newName.foreach { oProp.setName(_) }
    orientType.foreach { oType => oProp.setType(toOType(oType)) }
    typeClass.foreach { linkedClass =>
      {
        oProp.setLinkedClass(db.getMetadata.getSchema.getClass(linkedClass))
      }
    }
    constraints.foreach { setConstraints(oProp, _) }
  }

  private def setConstraints(oProp: OProperty, constraints: Constraints): Unit = {
    constraints.min.foreach { oProp.setMin(_) }
    constraints.max.foreach { oProp.setMax(_) }
    constraints.mandatory.foreach { oProp.setMandatory(_) }
    constraints.readOnly.foreach { oProp.setReadonly(_) }
    constraints.notNull.foreach { oProp.setNotNull(_) }
    constraints.regex.foreach { oProp.setRegexp(_) }
    constraints.collate.foreach { oProp.setCollate(_) }
    constraints.custom.foreach { cutomProp => oProp.setCustom(cutomProp.name, cutomProp.value) }
  }

  private def applyDropProperty(dropProperty: DropProperty): Unit = {
    val DropProperty(className, name) = dropProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    oClass.dropProperty(name)
  }

  private def applyCreateIndex(createIndex: CreateIndex): Unit = {
    val CreateIndex(className, name, indexType, properties) = createIndex
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    oClass.createIndex(name, toOIndexType(indexType), properties: _*)
  }

  private def applyDropIndex(dropIndex: DropIndex): Unit = {
    val DropIndex(name) = dropIndex
    db.getMetadata.getIndexManager.dropIndex(name)
  }

  private def applyCreateSequence(createSequence: CreateSequence): Unit = {
    val CreateSequence(name, sType, start, increment, cacheSize) = createSequence
    val sequenceLibrary = db.getMetadata.getSequenceLibrary

    val params = new OSequence.CreateParams()
    params.setStart(0).setIncrement(1)
    start.foreach { params.setStart(_) }
    increment.foreach { params.setIncrement(_) }
    cacheSize.foreach { params.setCacheSize(_) }

    sequenceLibrary.createSequence(name, sType match {
      case SequenceType.cached => SEQUENCE_TYPE.CACHED
      case SequenceType.ordered => SEQUENCE_TYPE.ORDERED
    }, params);
  }

  private def applyDropSequence(dropSequence: DropSequence): Unit = {
    val DropSequence(name) = dropSequence
    db.getMetadata.getSequenceLibrary.dropSequence(name)
  }
  
  private def applyRunSQLCommand(runSQLCommand: RunSQLCommand): Unit = {
    val RunSQLCommand(command) = runSQLCommand
    db.command(new OCommandSQL(command)).execute()
  }
  
  private def toOType(orientType: OrientType.Value): OType = {
    orientType match {
      case OrientType.BOOLEAN => OType.BOOLEAN
      case OrientType.INTEGER => OType.INTEGER
      case OrientType.SHORT => OType.SHORT
      case OrientType.LONG => OType.LONG
      case OrientType.FLOAT => OType.FLOAT
      case OrientType.DOUBLE => OType.DOUBLE
      case OrientType.DATETIME => OType.DATETIME
      case OrientType.STRING => OType.STRING
      case OrientType.BINARY => OType.BINARY
      case OrientType.EMBEDDED => OType.EMBEDDED
      case OrientType.EMBEDDEDLIST => OType.EMBEDDEDLIST
      case OrientType.EMBEDDEDSET => OType.EMBEDDEDSET
      case OrientType.EMBEDDEDMAP => OType.EMBEDDEDMAP
      case OrientType.LINK => OType.LINK
      case OrientType.LINKLIST => OType.LINKLIST
      case OrientType.LINKSET => OType.LINKSET
      case OrientType.LINKMAP => OType.LINKMAP
      case OrientType.BYTE => OType.BYTE
      case OrientType.TRANSIENT => OType.TRANSIENT
      case OrientType.DATE => OType.DATE
      case OrientType.CUSTOM => OType.CUSTOM
      case OrientType.DECIMAL => OType.DECIMAL
      case OrientType.LINKBAG => OType.LINKBAG
      case OrientType.ANY => OType.ANY
    }
  }
  
    private def toOIndexType(indexType: IndexType.Value): OClass.INDEX_TYPE = {
    indexType match {
      case IndexType.UNIQUE => OClass.INDEX_TYPE.UNIQUE
      case IndexType.NOTUNIQUE => OClass.INDEX_TYPE.NOTUNIQUE
      case IndexType.FULLTEXT => OClass.INDEX_TYPE.FULLTEXT
      case IndexType.DICTIONARY => OClass.INDEX_TYPE.DICTIONARY
      case IndexType.PROXY => OClass.INDEX_TYPE.PROXY
      case IndexType.UNIQUE_HASH_INDEX => OClass.INDEX_TYPE.UNIQUE_HASH_INDEX
      case IndexType.NOTUNIQUE_HASH_INDEX => OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX
      case IndexType.FULLTEXT_HASH_INDEX => OClass.INDEX_TYPE.FULLTEXT_HASH_INDEX
      case IndexType.DICTIONARY_HASH_INDEX => OClass.INDEX_TYPE.DICTIONARY_HASH_INDEX
      case IndexType.SPATIAL => OClass.INDEX_TYPE.SPATIAL
    }
  }

}
