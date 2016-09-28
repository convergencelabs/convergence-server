package com.convergencelabs.server.schema

object OrientType extends Enumeration {
  val  Boolean, Integer, Short, Long, Float, Double, 
  DateTime, String, Binary, 
  Embedded, EmbeddedList, EmbeddedSet, EmbeddedMap, 
  Link, LinkList, LinkSet, LinkMap, 
  Byte, Transient, Date, Custom, Decimal, LinkBag, Any = Value
}

object IndexType extends Enumeration {
  val Unique, NotUnique, FullText, 
  Dictionary, Proxy, UniqueHashIndex, 
  NotUniqueHashIndex, FullTextHashIndex, 
  DictionaryHashIndex, Spatial = Value
}

object SequenceType extends Enumeration {
  val Cached, Ordered = Value
}

case class Delta(version: Int, description: String, changes: List[Change])

sealed trait Change

case class CreateClass(name: String, superclass: Option[String], `abstract`: Option[Boolean], properties: List[Property]) extends Change
case class AlterClass(name: String, newName: Option[String], superclass: Option[String]) extends Change
case class DropClass(name: String) extends Change

case class AddProperty(className: String, property: Property) extends Change
case class AlterProperty(className: String, name: String, property: PropertyOptions) extends Change
case class DropProperty(className: String, name: String) extends Change

case class CreateIndex(className: String, name: String, `type`: IndexType.Value, properties: List[String]) extends Change
case class DropIndex(name: String) extends Change

case class CreateSequence(name: String, sequenceType: SequenceType.Value, start: Option[Int], increment: Option[Int], cacheSize: Option[Int]) extends Change
case class DropSequence(name: String) extends Change

case class RunSQLCommand(command: String) extends Change

case class CreateFunction(name: String, code: String, parameters: List[String], language: Option[String], idempotent: Option[Boolean]) extends Change
case class AlterFunction(name: String, newName: Option[String], code: Option[String], parameters: Option[List[String]], language: Option[String], idempotent: Option[Boolean]) extends Change
case class DropFunction(name: String) extends Change

case class Property(name: String, `type`: OrientType.Value, typeClass: Option[String], contraints: Option[Constraints])
case class PropertyOptions(name: Option[String], orientType: Option[OrientType.Value], typeClass: Option[String], contraints: Option[Constraints])


case class Constraints(
    min: Option[String], 
    max: Option[String], 
    mandatory: Option[Boolean], 
    readOnly: Option[Boolean], 
    notNull: Option[Boolean], 
    regex: Option[String],
    collate: Option[String],
    custom: Option[CustomProperty],
    default: Option[String])
    
case class CustomProperty(name: String, value: String)
    
