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

package com.convergencelabs.convergence.server.domain

package model {

  import com.convergencelabs.convergence.server.domain.model.reference.RangeReference
  import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

  case class ModelId(domainId: DomainId, modelId: String)


  case class ModelNotFoundException(modelId: String) extends Exception(s"A model with id '$modelId' does not exist.")

  //  case class ModelAlreadyExistsException(modelId: String) extends Exception(s"A model with id '$modelId' already exists.")

  case class CreateCollectionRequest(collection: Collection)

  case class UpdateCollectionRequest(collection: Collection)

  case class DeleteCollectionRequest(collectionId: String)

  case class GetCollectionRequest(collectionId: String)

  case object GetCollectionsRequest


  case class ClientDataRequestFailure(message: String) extends RuntimeException(message)

  case class ModelShutdownRequest(modelId: String, ephemeral: Boolean)

  case class ReferenceValue[V <: ModelReferenceValues](id: Option[String],
                                                          key: String,
                                                          referenceValues: V,
                                                          contextVersion: Long)

  case class ReferenceState(session: DomainUserSessionId,
                            valueId: Option[String],
                            key: String,
                            values: ModelReferenceValues)


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[RangeReferenceValues], name = "range"),
    new JsonSubTypes.Type(value = classOf[IndexReferenceValues], name = "index"),
    new JsonSubTypes.Type(value = classOf[PropertyReferenceValues], name = "property"),
    new JsonSubTypes.Type(value = classOf[ElementReferenceValues], name = "element")
  ))
  sealed trait ModelReferenceValues {
    def values: List[Any]
  }

  final case class RangeReferenceValues(values: List[RangeReference.Range]) extends ModelReferenceValues

  final case class IndexReferenceValues(values: List[Int]) extends ModelReferenceValues

  final case class PropertyReferenceValues(values: List[String]) extends ModelReferenceValues

  final case class ElementReferenceValues(values: List[String]) extends ModelReferenceValues

}
