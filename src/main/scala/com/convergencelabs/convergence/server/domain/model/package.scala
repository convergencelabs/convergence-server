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

import com.convergencelabs.convergence.server.datastore.domain.ModelPermissions
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue

package model {

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

  object ReferenceType extends Enumeration {
    val Index, Range, Property, Element = Value
  }

  case class ReferenceValue(id: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long)

  case class ReferenceState(
    session: DomainUserSessionId,
    valueId: Option[String],
    key: String,
    referenceType: ReferenceType.Value,
    values: List[Any])
}
