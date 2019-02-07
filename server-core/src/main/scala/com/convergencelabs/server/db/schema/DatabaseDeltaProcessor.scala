package com.convergencelabs.server.db.schema

import java.util.ArrayList

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.util.SafeTry
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.function.OFunction
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.metadata.schema.OProperty
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.metadata.sequence.OSequence
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object DatabaseDeltaProcessor {
  def apply(delta: Delta, db: ODatabaseDocument): Try[Unit] = new DatabaseDeltaProcessor(delta, db).apply()
}

class DatabaseDeltaProcessor(delta: Delta, db: ODatabaseDocument) extends Logging {

  private[this] var deferedLinkedProperties = Map[OProperty, String]();

  def apply(): Try[Unit] = SafeTry {
    debug(s"Applying delta: ${delta.version} to database")

    db.activateOnCurrentThread()

    debug(s"Applying ${delta.actions.size} actions for delta ${delta.version}")
    delta.actions foreach (change => applyChange(change))

    debug(s"Processing linked classes")
    processDeferedLinkedClasses()

    debug(s"Reloading database metadata")
    db.getMetadata.reload()

    debug(s"Finished applying delta ${delta.version} to database")
    (())
  } recoverWith {
    case cause: Throwable =>
      error(s"Applying delta ${delta.version} to database failed", cause)
      Failure(cause)
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
    newName.foreach(oClass.setName(_))
    superclass.foreach { supName =>
      val superClass: OClass = db.getMetadata.getSchema.getClass(supName)
      val arrayList = new ArrayList[OClass]()
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
            this.deferedLinkedProperties += (prop -> className)
            prop
        }
      case (Some(t), Some(c)) =>
        throw new IllegalArgumentException("Can not specify both a linked class and linked type")
    }
    constraints.foreach(setConstraints(oProp, _))
  }

  private[this] def applyAlterProperty(alterProperty: AlterProperty): Unit = {
    val AlterProperty(className, name, PropertyOptions(newName, orientType, linkedType, linkedClass, constraints)) = alterProperty
    val oClass: OClass = db.getMetadata.getSchema.getClass(className)
    val oProp: OProperty = oClass.getProperty(name)
    newName.foreach(oProp.setName(_))
    orientType.foreach(oType => oProp.setType(toOType(oType)))

    // FIXME at type, check to make sure both not set.
    linkedClass.foreach { linkedClass =>
      val clazz = db.getMetadata.getSchema.getClass(linkedClass)
      oProp.setLinkedClass(clazz)
    }

    constraints.foreach(setConstraints(oProp, _))
  }

  private[this] def setConstraints(oProp: OProperty, constraints: Constraints): Unit = {
    constraints.min.foreach(oProp.setMin(_))
    constraints.max.foreach(oProp.setMax(_))
    constraints.mandatory.foreach(oProp.setMandatory(_))
    constraints.readOnly.foreach(oProp.setReadonly(_))
    constraints.notNull.foreach(oProp.setNotNull(_))
    constraints.regex.foreach(oProp.setRegexp(_))
    constraints.collate.foreach(oProp.setCollate(_))
    constraints.custom.foreach(cutomProp => oProp.setCustom(cutomProp.name, cutomProp.value))
    constraints.default.foreach(oProp.setDefaultValue(_))
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

    val index = oClass.createIndex(name, toOIndexType(indexType).toString, null, metaDataDoc, properties: _*)
  }

  private[this] def applyDropIndex(dropIndex: DropIndex): Unit = {
    val DropIndex(name) = dropIndex
    db.getMetadata.getIndexManager.dropIndex(name)
  }

  private[this] def applyCreateSequence(createSequence: CreateSequence): Unit = {
    val CreateSequence(name, sType, start, increment, cacheSize) = createSequence
    val sequenceLibrary = db.getMetadata.getSequenceLibrary

    val params = new OSequence.CreateParams()
    params.setStart(0).setIncrement(1)
    start.foreach(params.setStart(_))
    increment.foreach(params.setIncrement(_))
    cacheSize.foreach(params.setCacheSize(_))

    sequenceLibrary.createSequence(name, sType match {
      case SequenceType.Cached => SEQUENCE_TYPE.CACHED
      case SequenceType.Ordered => SEQUENCE_TYPE.ORDERED
    }, params);
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
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
    function.save()
  }

  private[this] def applyAlterFunction(alterFunction: AlterFunction): Unit = {
    val AlterFunction(name, newName, code, parameters, language, idempotent) = alterFunction
    val function: OFunction = db.getMetadata.getFunctionLibrary.getFunction(name)

    newName.foreach { function.setName(_) }
    code.foreach { function.setCode(_) }
    parameters.foreach { params => function.setParameters(params.asJava) }
    language.foreach { function.setLanguage(_) }
    idempotent.foreach { function.setIdempotent(_) }
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
      case IndexType.FullTextHashIndex => OClass.INDEX_TYPE.FULLTEXT_HASH_INDEX
      case IndexType.DictionaryHashIndex => OClass.INDEX_TYPE.DICTIONARY_HASH_INDEX
      case IndexType.Spatial => OClass.INDEX_TYPE.SPATIAL
    }
  }

  private[this] def processDeferedLinkedClasses(): Unit = {
    deferedLinkedProperties map {
      case (prop, className) =>
        Option(db.getMetadata().getSchema().getClass(className)) match {
          case Some(linkedClass) =>
            prop.setLinkedClass(linkedClass)
          case None =>
            throw new IllegalStateException(
              s"Could not set linked class because the class does not exist: ${className}")
        }
    }
  }
}
