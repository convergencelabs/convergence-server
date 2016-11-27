package com.convergencelabs.server.db.schema

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
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import com.orientechnologies.orient.core.record.impl.ODocument

class DatabaseSchemaProcessor(dbPool: OPartitionedDatabasePool) {

  // TODO: Implement Rollback Strategy

  def applyDelta(delta: Delta): Try[Unit] = Try {
    delta.changes foreach { change => applyChange(change) }
  }

  private def applyChange(change: Change): Try[Unit] = {
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
      case runSQLCommand: RunSQLCommand => applyRunSQLCommand(runSQLCommand)
      case createFunction: CreateFunction => applyCreateFunction(createFunction)
      case alterFunction: AlterFunction => applyAlterFunction(alterFunction)
      case dropFunction: DropFunction => applyDropFunction(dropFunction)
    }
  }

  private def applyCreateClass(createClass: CreateClass): Try[Unit] = Try {
    val CreateClass(name, superclass, isAbstract, properties) = createClass
    val db = dbPool.acquire()
    val sClass = superclass.map { db.getMetadata.getSchema.getClass(_) }

    val newClass = (isAbstract, sClass) match {
      case (Some(true), Some(oClass)) => db.getMetadata.getSchema.createAbstractClass(name, oClass)
      case (_, Some(oClass)) => db.getMetadata.getSchema.createClass(name, oClass)
      case (Some(true), None) => db.getMetadata.getSchema.createAbstractClass(name)
      case (_, None) => db.getMetadata.getSchema.createClass(name)
    }

    properties foreach { addProperty(newClass, _) }
    db.close()
  }

  private def applyAlterClass(alterClass: AlterClass): Try[Unit] = Try {
    val AlterClass(name, newName, superclass) = alterClass
    val db = dbPool.acquire()
    val oClass: OClass = db.getMetadata.getSchema.getClass(name)
    newName.foreach { oClass.setName(_) }
    superclass.foreach { supName =>
      val superClass: OClass = db.getMetadata.getSchema.getClass(supName)
      val arrayList = new ArrayList[OClass]()
      arrayList.add(superClass)
      oClass.setSuperClasses(arrayList)
    }
    db.close()
  }

  private def applyDropClass(dropClass: DropClass): Try[Unit] = Try {
    val DropClass(name) = dropClass
    val db = dbPool.acquire()
    db.getMetadata.getSchema.dropClass(name)
    db.close()
  }

  private def applyAddProperty(addProperty: AddProperty): Try[Unit] = Try {
    val AddProperty(className, property) = addProperty
    val db = dbPool.acquire()
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    this.addProperty(oClass, property)
    db.close()
  }

  private def addProperty(oClass: OClass, property: Property): Unit = {
    val Property(name, orientType, linkedType, linkedClass, constraints) = property
    println(s"${oClass.getName} - ${property}")
    val db = dbPool.acquire()
    val oProp: OProperty = (linkedType, linkedClass) match {
      case (Some(t), None) =>
        oClass.createProperty(name, toOType(orientType), toOType(t))
      case (Some(t), Some(c)) =>
        throw new IllegalArgumentException("Can not specify both a linked class and linked type")
      case (None, Some(c)) =>
        val linkedClass = db.getMetadata.getSchema.getClass(c)
        oClass.createProperty(name, toOType(orientType), linkedClass)
      case (None, None) =>
        oClass.createProperty(name, toOType(orientType))
    }
    constraints.foreach { setConstraints(oProp, _) }
    db.close()
  }

  private def applyAlterProperty(alterProperty: AlterProperty): Try[Unit] = Try {
    val AlterProperty(className, name, PropertyOptions(newName, orientType, linkedType, linkedClass, constraints)) = alterProperty
    val db = dbPool.acquire()
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    val oProp: OProperty = oClass.getProperty(name)
    newName.foreach { oProp.setName(_) }
    orientType.foreach { oType => oProp.setType(toOType(oType)) }

    // FIXME at type, check to make sure both not set.
    linkedClass.foreach { linkedClass =>
      val clazz = db.getMetadata.getSchema.getClass(linkedClass)
      oProp.setLinkedClass(clazz)
    }

    constraints.foreach { setConstraints(oProp, _) }
    db.close()
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

  private def applyDropProperty(dropProperty: DropProperty): Try[Unit] = Try {
    val DropProperty(className, name) = dropProperty
    val db = dbPool.acquire()
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    oClass.dropProperty(name)
    db.close()
  }

  private def applyCreateIndex(createIndex: CreateIndex): Try[Unit] = Try {
    val CreateIndex(className, name, indexType, properties, metaData) = createIndex
    val db = dbPool.acquire()
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    
    val metaDataDoc = metaData match {
      case Some(map) =>
        new ODocument().fromMap(map.asJava)
      case None =>
        new ODocument()
    }
    
    val index = oClass.createIndex(name, toOIndexType(indexType).toString, null, metaDataDoc, properties: _*)
    db.close()
  }

  private def applyDropIndex(dropIndex: DropIndex): Try[Unit] = Try {
    val DropIndex(name) = dropIndex
    val db = dbPool.acquire()
    db.getMetadata.getIndexManager.dropIndex(name)
    db.close()
  }

  private def applyCreateSequence(createSequence: CreateSequence): Try[Unit] = Try {
    val CreateSequence(name, sType, start, increment, cacheSize) = createSequence
    val db = dbPool.acquire()
    val sequenceLibrary = db.getMetadata.getSequenceLibrary

    val params = new OSequence.CreateParams()
    params.setStart(0).setIncrement(1)
    start.foreach { params.setStart(_) }
    increment.foreach { params.setIncrement(_) }
    cacheSize.foreach { params.setCacheSize(_) }

    sequenceLibrary.createSequence(name, sType match {
      case SequenceType.Cached => SEQUENCE_TYPE.CACHED
      case SequenceType.Ordered => SEQUENCE_TYPE.ORDERED
    }, params);
    db.close()
  }

  private def applyDropSequence(dropSequence: DropSequence): Try[Unit] = Try {
    val DropSequence(name) = dropSequence
    val db = dbPool.acquire()
    db.getMetadata.getSequenceLibrary.dropSequence(name)
    db.close()
  }

  private def applyRunSQLCommand(runSQLCommand: RunSQLCommand): Try[Unit] = Try {
    val RunSQLCommand(command) = runSQLCommand
    val db = dbPool.acquire()
    db.command(new OCommandSQL(command)).execute()
    db.close()
  }

  private def applyCreateFunction(createFunction: CreateFunction): Try[Unit] = Try {
    val CreateFunction(name, code, parameters, language, idempotent) = createFunction
    val db = dbPool.acquire()
    val function: OFunction = db.getMetadata.getFunctionLibrary.createFunction(name)

    function.setCode(code)
    function.setParameters(parameters.asJava)
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
    function.save()
  }

  private def applyAlterFunction(alterFunction: AlterFunction): Try[Unit] = Try {
    val AlterFunction(name, newName, code, parameters, language, idempotent) = alterFunction
    val db = dbPool.acquire()
    val function: OFunction = db.getMetadata.getFunctionLibrary.getFunction(name)

    newName.foreach { function.setName(_) }
    code.foreach { function.setCode(_) }
    parameters.foreach { params => function.setParameters(params.asJava) }
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
    function.save()
    db.close()
  }

  private def applyDropFunction(dropFunction: DropFunction): Try[Unit] = Try {
    val DropFunction(name) = dropFunction
    val db = dbPool.acquire()
    db.getMetadata.getFunctionLibrary.dropFunction(name)
    db.close()
  }

  private def toOType(orientType: OrientType.Value): OType = {
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

  private def toOIndexType(indexType: IndexType.Value): OClass.INDEX_TYPE = {
    indexType match {
      case IndexType.Unique => OClass.INDEX_TYPE.UNIQUE
      case IndexType.NotUnique => OClass.INDEX_TYPE.NOTUNIQUE
      case IndexType.FullText => OClass.INDEX_TYPE.FULLTEXT
      case IndexType.Dictionary => OClass.INDEX_TYPE.DICTIONARY
      case IndexType.Proxy => OClass.INDEX_TYPE.PROXY
      case IndexType.UniqueHashIndex => OClass.INDEX_TYPE.UNIQUE_HASH_INDEX
      case IndexType.NotUniqueHashIndex => OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX
      case IndexType.FullTextHashIndex => OClass.INDEX_TYPE.FULLTEXT_HASH_INDEX
      case IndexType.DictionaryHashIndex => OClass.INDEX_TYPE.DICTIONARY_HASH_INDEX
      case IndexType.Spatial => OClass.INDEX_TYPE.SPATIAL
    }
  }

}
