package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.domain.ModelPermissionsStore._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import scala.util.Try
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.{ List => JavaList }
import com.orientechnologies.orient.core.metadata.schema.OType
import java.util.HashSet
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.EntityNotFoundException

case class ModelPermissions(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean)
case class ModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions)

case class UserRoles(username: String, roles: Set[String])

object ModelPermissionsStore {
  val ModelClass = "Model"
  val CollectionClass = "Collection"

  val CollectionPermissionsClass = "CollectionPermissions"
  val CollectionUserPermissionsClass = "CollectionUserPermissions"

  val ModelPermissionsClass = "ModelPermissions"
  val ModelUserPermissionsClass = "ModelUserPermissions"

  val CollectionIndex = "Collection.id"
  val ModelIndex = "Model.collection_id"
  val UsernameIndex = "User.username"
  val CollectionUserPermissionsIndex = "CollectionUserPermissions.user_collection"
  val ModelUserPermissionsIndex = "ModelUserPermissions.user_model"

  object Fields {
    val Collection = "collection"
    val Model = "model"
    val User = "user"
    val Permissions = "permissions"

    val World = "world"

    val Username = "username"

    val Read = "read"
    val Write = "write"
    val Remove = "remove"
    val Manage = "manage"

    val Create = "create"
  }

  def docToWorldPermissions(doc: ODocument): Option[ModelPermissions] = {
    val world: ODocument = doc.field(Fields.World)
    Option(world).map { worldDoc =>
      ModelPermissions(
        worldDoc.field(Fields.Read),
        worldDoc.field(Fields.Write),
        worldDoc.field(Fields.Remove),
        worldDoc.field(Fields.Manage))
    }
  }

  def docToModelPermissions(doc: ODocument): ModelPermissions = {
    ModelPermissions(
      doc.field(Fields.Read),
      doc.field(Fields.Write),
      doc.field(Fields.Remove),
      doc.field(Fields.Manage))
  }

  def modelPermissionToDoc(permissions: ModelPermissions): ODocument = {
    val doc = new ODocument()
    doc.field(Fields.Read, permissions.read)
    doc.field(Fields.Write, permissions.write)
    doc.field(Fields.Remove, permissions.remove)
    doc.field(Fields.Manage, permissions.manage)
    doc
  }
}

class ModelPermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getModelWorldPermissions(modelFqn: ModelFqn): Try[Option[ModelPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT world
        |  FROM Model
        |  WHERE id = :modelId AND
        |    collection.id = :collectionId""".stripMargin
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db)
    result.map { docToWorldPermissions(_) }.get
  }

  def setModelWorldPermissions(modelFqn: ModelFqn, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    val modelDoc = getModelRid(modelFqn).get.getRecord[ODocument]
    val permissionsDoc = modelPermissionToDoc(permissions)
    modelDoc.fields(Fields.World, permissionsDoc)
    modelDoc.save()
  }

  def getAllModelUserPermissions(modelFqn: ModelFqn): Try[List[ModelUserPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT user.username as username, permissions
        |  FROM ModelUserPermissions
        |  WHERE model.id = :modelId AND
        |    model.collection.id = :collectionId""".stripMargin
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId)
    val results = QueryUtil.query(queryString, params, db)
    results.map { result =>
      val username = result.field(Fields.Username)
      val permissions = docToModelPermissions(result.field(Fields.Permissions))
      ModelUserPermissions(modelFqn, username, permissions)
    }
  }

  def getModelUserPermissions(modelFqn: ModelFqn, username: String): Try[Option[ModelPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT permissions
        |  FROM ModelUserPermissions
        |  WHERE model.id = :modelId AND
        |    model.collection.id = :collectionId AND
        |    user.username = :username""".stripMargin
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId, "username" -> username)
    val result = QueryUtil.lookupOptionalDocument(queryString, params, db)
    result.map { doc => docToModelPermissions(doc) }
  }

  def setModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    ???
  }

  def removeModelUserPermissions(modelFqn: ModelFqn, username: String): Try[Unit] = tryWithDb { db =>
    val queryString =
      """DELETE FROM ModelUserPermissions
        |  WHERE model.id = :modelId AND
        |    model.collection.id = :collectionId AND
        |    user.username = :username""".stripMargin
    val command = new OCommandSQL(queryString)
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId, "username" -> username)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 =>
        throw EntityNotFoundException()
      case _ =>
        ()
    }
  }

  def getUserRid(username: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(UsernameIndex, username, db).get
  }

  def getCollectionRid(collectionId: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(CollectionIndex, collectionId, db).get
  }

  def getModelRid(modelFqn: ModelFqn): Try[ORID] = tryWithDb { db =>
    val ModelFqn(collectionId, modelId) = modelFqn
    val collectionRID = getCollectionRid(collectionId).get
    val key = new OCompositeKey(List(collectionRID, modelId).asJava)
    QueryUtil.getRidFromIndex(ModelIndex, key, db).get
  }

  def getModelUserPermissionsRid(modelFqn: ModelFqn, user: String): Try[ORID] = tryWithDb { db =>
    val ModelFqn(collectionId, modelId) = modelFqn
    val collectionRID = getCollectionRid(collectionId).get
    val key = new OCompositeKey(List(collectionRID, modelId).asJava)
    QueryUtil.getRidFromIndex(ModelIndex, key, db).get
  }
}
