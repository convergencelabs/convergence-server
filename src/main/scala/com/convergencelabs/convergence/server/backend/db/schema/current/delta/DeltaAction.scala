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

package com.convergencelabs.convergence.server.backend.db.schema.current.delta

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration

/**
 * The [[DeltaAction]] trait is the sealed set of classes that represents
 * individual instruction to modify the database.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[CreateClass]),
  new JsonSubTypes.Type(value = classOf[AlterClass]),
  new JsonSubTypes.Type(value = classOf[DropClass]),
  new JsonSubTypes.Type(value = classOf[AddProperty]),
  new JsonSubTypes.Type(value = classOf[AlterProperty]),
  new JsonSubTypes.Type(value = classOf[DropProperty]),
  new JsonSubTypes.Type(value = classOf[CreateIndex]),
  new JsonSubTypes.Type(value = classOf[DropIndex]),
  new JsonSubTypes.Type(value = classOf[CreateSequence]),
  new JsonSubTypes.Type(value = classOf[DropSequence]),
  new JsonSubTypes.Type(value = classOf[RunSqlCommand]),
  new JsonSubTypes.Type(value = classOf[CreateFunction]),
  new JsonSubTypes.Type(value = classOf[AlterFunction]),
  new JsonSubTypes.Type(value = classOf[DropFunction])
))
private[schema] sealed trait DeltaAction

private[schema] final case class CreateClass(name: String,
                                             superclass: Option[String],
                                             `abstract`: Option[Boolean],
                                             properties: List[Property]) extends DeltaAction

private[schema] final case class AlterClass(name: String,
                                            newName: Option[String],
                                            superclass: Option[String]) extends DeltaAction

private[schema] final case class DropClass(name: String) extends DeltaAction

private[schema] final case class AddProperty(className: String,
                                             property: Property) extends DeltaAction

private[schema] final case class AlterProperty(className: String,
                                               name: String,
                                               property: PropertyOptions) extends DeltaAction

private[schema] final case class DropProperty(className: String,
                                              name: String) extends DeltaAction

private[schema] final case class CreateIndex(className: String,
                                             name: String,
                                             @JsonScalaEnumeration(classOf[IndexTypeTypeReference])
                                             `type`: IndexType.Value,
                                             properties: List[String],
                                             metaData: Option[Map[String, Any]]) extends DeltaAction

private[schema] final case class DropIndex(name: String) extends DeltaAction

private[schema] final case class CreateSequence(name: String,
                                                @JsonScalaEnumeration(classOf[SequenceTypeTypeReference])
                                                sequenceType: SequenceType.Value,
                                                start: Option[Long],
                                                increment: Option[Int],
                                                cacheSize: Option[Int]) extends DeltaAction

private[schema] final case class DropSequence(name: String) extends DeltaAction

private[schema] final case class RunSqlCommand(command: String) extends DeltaAction

private[schema] final case class CreateFunction(name: String,
                                                code: String,
                                                parameters: List[String],
                                                language: Option[String],
                                                idempotent: Option[Boolean]) extends DeltaAction

private[schema] final case class AlterFunction(name: String,
                                               newName: Option[String],
                                               code: Option[String],
                                               parameters: Option[List[String]],
                                               language: Option[String],
                                               idempotent: Option[Boolean]) extends DeltaAction

private[schema] final case class DropFunction(name: String) extends DeltaAction
