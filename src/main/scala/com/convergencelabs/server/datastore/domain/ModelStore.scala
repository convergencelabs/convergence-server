package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ot.ops.Operation
import org.json4s.JsonAST.JValue

trait ModelStore {

  def modelExists(fqn: ModelFqn): Boolean

  def createModel(fqn: ModelFqn, data: JValue, creationTime: Long): Unit

  def deleteModel(fqn: ModelFqn): Unit

  def getModelMetaData(fqn: ModelFqn): Option[ModelMetaData]

  def getModelData(fqn: ModelFqn): Option[ModelData]

  def getModelJsonData(fqn: ModelFqn): Option[JValue]

  def applyOperationToModel(fqn: ModelFqn, operation: Operation, version: Long, timestamp: Long, username: String): Unit

  def getModelFieldDataType(fqn: ModelFqn, path: List[Any]): Option[DataType.Value]

  def getAllModels(orderBy: String, ascending: Boolean, offset: Int, limit: Int): List[ModelMetaData]

  def getAllModelsInCollection(collectionId: String, orderBy: String, ascending: Boolean, offset: Int, limit: Int): List[ModelMetaData]
}

case class ModelData(metaData: ModelMetaData, data: JValue)
case class ModelMetaData(fqn: ModelFqn, version: Long, createdTime: Long, modifiedTime: Long)

object DataType extends Enumeration {
  val ARRAY, OBJECT,  STRING, NUMBER, BOOLEAN, NULL = Value
}
