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
import com.orientechnologies.orient.core.metadata.function.OFunction

object OrientSchemaProcessor {
  def main(args: Array[String]): Unit = {
    val db = new ODatabaseDocumentTx("memory:export" + System.nanoTime())
    db.create()
    val processor = new OrientSchemaProcessor(db)

    val delta = Delta(1, "Test", List(CreateClass("myClass", None, None, List())))
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
      case createFunction: CreateFunction => applyCreateFunction(createFunction)
      case alterFunction: AlterFunction   => applyAlterFunction(alterFunction)
      case dropFunction: DropFunction     => applyDropFunction(dropFunction)
    }
  }

  private def applyCreateClass(createClass: CreateClass): Unit = {
    val CreateClass(name, superclass, isAbstract, properties) = createClass
    val sClass = superclass.map { db.getMetadata.getSchema.getClass(_) }

    val newClass = (isAbstract, sClass) match {
      case (Some(true), Some(oClass)) => db.getMetadata.getSchema.createAbstractClass(name, oClass)
      case (_, Some(oClass))          => db.getMetadata.getSchema.createClass(name, oClass)
      case (Some(true), None)         => db.getMetadata.getSchema.createAbstractClass(name)
      case (_, None)                  => db.getMetadata.getSchema.createClass(name)
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
    constraints.default.foreach { oProp.setDefaultValue(_) }
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
      case SequenceType.Cached  => SEQUENCE_TYPE.CACHED
      case SequenceType.Ordered => SEQUENCE_TYPE.ORDERED
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

  private def applyCreateFunction(createFunction: CreateFunction): Unit = {
    val CreateFunction(name, code, parameters, language, idempotent) = createFunction
    val function: OFunction = db.getMetadata.getFunctionLibrary.createFunction(name)

    function.setCode(code)
    function.setParameters(parameters.asJava)
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
  }

  private def applyAlterFunction(alterFunction: AlterFunction): Unit = {
    val AlterFunction(name, newName, code, parameters, language, idempotent) = alterFunction
    val function: OFunction = db.getMetadata.getFunctionLibrary.getFunction(name)

    newName.foreach { function.setName(_) }
    code.foreach { function.setCode(_) }
    parameters.foreach { params => function.setParameters(params.asJava) }
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
  }

  private def applyDropFunction(dropFunction: DropFunction): Unit = {
    val DropFunction(name) = dropFunction
    db.getMetadata.getFunctionLibrary.dropFunction(name)
  }

  private def toOType(orientType: OrientType.Value): OType = {
    orientType match {
      case OrientType.Boolean      => OType.BOOLEAN
      case OrientType.Integer      => OType.INTEGER
      case OrientType.Short        => OType.SHORT
      case OrientType.Long         => OType.LONG
      case OrientType.Float        => OType.FLOAT
      case OrientType.Double       => OType.DOUBLE
      case OrientType.DateTime     => OType.DATETIME
      case OrientType.String       => OType.STRING
      case OrientType.Binary       => OType.BINARY
      case OrientType.Embedded     => OType.EMBEDDED
      case OrientType.EmbeddedList => OType.EMBEDDEDLIST
      case OrientType.EmbeddedSet  => OType.EMBEDDEDSET
      case OrientType.EmbeddedMap  => OType.EMBEDDEDMAP
      case OrientType.Link         => OType.LINK
      case OrientType.LinkList     => OType.LINKLIST
      case OrientType.LinkSet      => OType.LINKSET
      case OrientType.LinkMap      => OType.LINKMAP
      case OrientType.Byte         => OType.BYTE
      case OrientType.Transient    => OType.TRANSIENT
      case OrientType.Date         => OType.DATE
      case OrientType.Custom       => OType.CUSTOM
      case OrientType.Decimal      => OType.DECIMAL
      case OrientType.LinkBag      => OType.LINKBAG
      case OrientType.Any          => OType.ANY
    }
  }

  private def toOIndexType(indexType: IndexType.Value): OClass.INDEX_TYPE = {
    indexType match {
      case IndexType.Unique              => OClass.INDEX_TYPE.UNIQUE
      case IndexType.NotUnique           => OClass.INDEX_TYPE.NOTUNIQUE
      case IndexType.FullText            => OClass.INDEX_TYPE.FULLTEXT
      case IndexType.Dictionary          => OClass.INDEX_TYPE.DICTIONARY
      case IndexType.Proxy               => OClass.INDEX_TYPE.PROXY
      case IndexType.UniqueHashIndex     => OClass.INDEX_TYPE.UNIQUE_HASH_INDEX
      case IndexType.NotUniqueHashIndex  => OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX
      case IndexType.FullTextHashIndex   => OClass.INDEX_TYPE.FULLTEXT_HASH_INDEX
      case IndexType.DictionaryHashIndex => OClass.INDEX_TYPE.DICTIONARY_HASH_INDEX
      case IndexType.Spatial             => OClass.INDEX_TYPE.SPATIAL
    }
  }

}
