package com.convergencelabs.server.schema

object SequenceType extends Enumeration {
  val cached, ordered = Value
}

object OrientType extends Enumeration {
  val  BOOLEAN, INTEGER, SHORT, LONG, FLOAT, DOUBLE, 
  DATETIME, STRING, BINARY, 
  EMBEDDED, EMBEDDEDLIST, EMBEDDEDSET, EMBEDDEDMAP, 
  LINK, LINKLIST, LINKSET, LINKMAP, 
  BYTE, TRANSIENT, DATE, CUSTOM, DECIMAL, LINKBAG, ANY = Value
}

object IndexType extends Enumeration {
  val UNIQUE, NOTUNIQUE, FULLTEXT, 
  DICTIONARY, PROXY, UNIQUE_HASH_INDEX, 
  NOTUNIQUE_HASH_INDEX, FULLTEXT_HASH_INDEX, 
  DICTIONARY_HASH_INDEX, SPATIAL = Value
}

case class Delta(version: Int, description: String, changes: List[Change])

sealed trait Change

case class CreateClass(name: String, superclass: Option[String], properties: List[Property]) extends Change
case class AlterClass(name: String, newName: Option[String], superclass: Option[String]) extends Change
case class DropClass(name: String) extends Change

case class AddProperty(className: String, property: Property) extends Change
case class AlterProperty(className: String, name: String, property: PropertyOptions) extends Change
case class DropProperty(className: String, name: String) extends Change

case class CreateIndex(className: String, name: String, indexType: IndexType.Value, properties: List[String]) extends Change
case class DropIndex(name: String) extends Change

case class CreateSequence(name: String, sequenceType: SequenceType.Value, start: Option[Int], increment: Option[Int], cacheSize: Option[Int]) extends Change
case class DropSequence(name: String) extends Change

case class RunSQLCommand(command: String) extends Change

case class Property(name: String, orientType: OrientType.Value, typeClass: Option[String], contraints: Option[Constraints])
case class PropertyOptions(name: Option[String], orientType: Option[OrientType.Value], typeClass: Option[String], contraints: Option[Constraints])


case class Constraints(
    min: Option[String], 
    max: Option[String], 
    mandatory: Option[Boolean], 
    readOnly: Option[Boolean], 
    notNull: Option[Boolean], 
    regex: Option[String],
    collate: Option[String],
    custom: Option[CustomProperty])
    
case class CustomProperty(name: String, value: String)
    
