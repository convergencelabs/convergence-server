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

package com.convergencelabs.convergence.server.db.schema

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

case class DeltaScript(rawScript: String, delta: Delta)
case class Delta(version: Int, description: Option[String], actions: List[DeltaAction])

sealed trait DeltaAction

case class CreateClass(name: String, superclass: Option[String], `abstract`: Option[Boolean], properties: List[Property]) extends DeltaAction
case class AlterClass(name: String, newName: Option[String], superclass: Option[String]) extends DeltaAction
case class DropClass(name: String) extends DeltaAction

case class AddProperty(className: String, property: Property) extends DeltaAction
case class AlterProperty(className: String, name: String, property: PropertyOptions) extends DeltaAction
case class DropProperty(className: String, name: String) extends DeltaAction

case class CreateIndex(className: String, name: String, `type`: IndexType.Value, properties: List[String], metaData: Option[Map[String, Any]]) extends DeltaAction
case class DropIndex(name: String) extends DeltaAction

case class CreateSequence(name: String, sequenceType: SequenceType.Value, start: Option[Long], increment: Option[Int], cacheSize: Option[Int]) extends DeltaAction
case class DropSequence(name: String) extends DeltaAction

case class RunSqlCommand(command: String) extends DeltaAction

case class CreateFunction(name: String, code: String, parameters: List[String], language: Option[String], idempotent: Option[Boolean]) extends DeltaAction
case class AlterFunction(name: String, newName: Option[String], code: Option[String], parameters: Option[List[String]], language: Option[String], idempotent: Option[Boolean]) extends DeltaAction
case class DropFunction(name: String) extends DeltaAction

case class Property(name: String, `type`: OrientType.Value, linkedType: Option[OrientType.Value], linkedClass: Option[String], constraints: Option[Constraints])
case class PropertyOptions(name: Option[String], orientType: Option[OrientType.Value], linkedType: Option[OrientType.Value], linkedClass: Option[String], constraints: Option[Constraints])


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
