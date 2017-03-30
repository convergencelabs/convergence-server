package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.domain.ModelPermissionsStore._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import scala.util.Try
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.asJavaCollectionConverter
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
import com.orientechnologies.orient.core.db.record.OIdentifiable
import java.util.ArrayList

case class ModelPermissions(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean)
case class CollectionPermissions(create: Boolean, read: Boolean, write: Boolean, remove: Boolean, manage: Boolean)

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

    val World = "worldPermissions"

    val Username = "username"

    val Read = "read"
    val Write = "write"
    val Remove = "remove"
    val Manage = "manage"

    val Create = "create"
  }

  def docToCollectionWorldPermissions(doc: ODocument): Option[CollectionPermissions] = {
    val world: ODocument = doc.field(Fields.World)
    Option(world).map { worldDoc =>
      CollectionPermissions(
        worldDoc.field(Fields.Create),
        worldDoc.field(Fields.Read),
        worldDoc.field(Fields.Write),
        worldDoc.field(Fields.Remove),
        worldDoc.field(Fields.Manage))
    }
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

  def docToCollectionPermissions(doc: ODocument): CollectionPermissions = {
    CollectionPermissions(
      doc.field(Fields.Create),
      doc.field(Fields.Read),
      doc.field(Fields.Write),
      doc.field(Fields.Remove),
      doc.field(Fields.Manage))
  }

  def docToModelPermissions(doc: ODocument): ModelPermissions = {
    ModelPermissions(
      doc.field(Fields.Read),
      doc.field(Fields.Write),
      doc.field(Fields.Remove),
      doc.field(Fields.Manage))
  }

  def collectionPermissionToDoc(permissions: CollectionPermissions): ODocument = {
    val doc = new ODocument(CollectionPermissionsClass)
    doc.field(Fields.Create, permissions.create)
    doc.field(Fields.Read, permissions.read)
    doc.field(Fields.Write, permissions.write)
    doc.field(Fields.Remove, permissions.remove)
    doc.field(Fields.Manage, permissions.manage)
    doc
  }

  def modelPermissionToDoc(permissions: ModelPermissions): ODocument = {
    val doc = new ODocument(ModelPermissionsClass)
    doc.field(Fields.Read, permissions.read)
    doc.field(Fields.Write, permissions.write)
    doc.field(Fields.Remove, permissions.remove)
    doc.field(Fields.Manage, permissions.manage)
    doc
  }
}

class ModelPermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getCollectionWorldPermissions(collectionId: String): Try[Option[CollectionPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT worldPermissions
        |  FROM Collection
        |  WHERE id = :collectionId""".stripMargin
    val params = Map("collectionId" -> collectionId)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db)
    result.map { docToCollectionWorldPermissions(_) }.get
  }

  def setCollectionWorldPermissions(collectionId: String, permissions: Option[CollectionPermissions]): Try[Unit] = tryWithDb { db =>
    val collectionDoc = getCollectionRid(collectionId).get.getRecord[ODocument]
    val permissionsDoc = permissions.map { collectionPermissionToDoc(_) }
    collectionDoc.fields(Fields.World, permissionsDoc.getOrElse(null))
    collectionDoc.save()
  }

  def getAllCollectionUserPermissions(collectionId: String): Try[Map[String, CollectionPermissions]] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val collectionDoc = collectionRID.getRecord[ODocument]
    val userPermissions: JavaList[ODocument] = collectionDoc.field("userPermissions", OType.LINKLIST)
    if (userPermissions == null) {
      Map[String, CollectionPermissions]()
    } else {
      userPermissions.asScala.map { userPermission =>
        val user: ODocument = userPermission.field(Fields.User)
        val username: String = user.field(Fields.Username)
        val permissions = docToCollectionPermissions(userPermission.field(Fields.Permissions))
        (username -> permissions)
      }.toMap
    }
  }

  def deleteAllCollectionUserPermissions(collectionId: String): Try[Unit] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val collectionDoc = collectionRID.getRecord[ODocument]
    collectionDoc.field("userPermissions", new ArrayList[ODocument]())
    collectionDoc.save()

    val queryString =
      """DELETE FROM CollectionUserPermissions
        |  WHERE collection.id = :collectionId""".stripMargin
    val command = new OCommandSQL(queryString)
    val params = Map("collectionId" -> collectionId)
    db.command(command).execute(params.asJava)
  }

  def updateAllCollectionUserPermissions(collectionId: String, userPermissions: Map[String, Option[CollectionPermissions]]): Try[Unit] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get

    userPermissions.foreach {
      case (username, permissions) =>
        val userRID = DomainUserStore.getUserRid(username, db).get
        val key = new OCompositeKey(List(userRID, collectionRID).asJava)
        val collectionPermissionRID = getCollectionUserPermissionsRid(collectionId, username)
        if (collectionPermissionRID.isSuccess) {
          db.delete(collectionPermissionRID.get)
        }

        permissions.foreach { perm =>
          val collectionPermissionsDoc = db.newInstance(CollectionUserPermissionsClass)
          collectionPermissionsDoc.field(Fields.Collection, collectionRID)
          collectionPermissionsDoc.field(Fields.User, userRID)
          collectionPermissionsDoc.field(Fields.Permissions, collectionPermissionToDoc(perm))
          collectionPermissionsDoc.save()
        }
    }
  }

  def getCollectionUserPermissions(collectionId: String, username: String): Try[Option[CollectionPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT permissions
        |  FROM CollectionUserPermissions
        |  WHERE collection.id = :collectionId AND
        |    user.username = :username""".stripMargin
    val params = Map("collectionId" -> collectionId, "username" -> username)
    val result = QueryUtil.lookupOptionalDocument(queryString, params, db)
    result.map { doc => docToCollectionPermissions(doc.field("permissions")) }
  }

  def updateCollectionUserPermissions(collectionId: String, username: String, permissions: CollectionPermissions): Try[Unit] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, collectionRID).asJava)
    val collectionPermissionRID = getCollectionUserPermissionsRid(collectionId, username)
    if (collectionPermissionRID.isSuccess) {
      val collectionPermissionsDoc = collectionPermissionRID.get.getRecord[ODocument]
      collectionPermissionsDoc.field(Fields.Permissions, collectionPermissionToDoc(permissions))
      collectionPermissionsDoc.save()
    } else {
      var collectionPermissionsDoc = db.newInstance(CollectionUserPermissionsClass)
      collectionPermissionsDoc.field(Fields.Collection, collectionRID)
      collectionPermissionsDoc.field(Fields.User, userRID)
      collectionPermissionsDoc.field(Fields.Permissions, collectionPermissionToDoc(permissions))
      collectionPermissionsDoc = collectionPermissionsDoc.save()

      val collection = collectionRID.getRecord[ODocument]
      var userPermissions: JavaList[ODocument] = collection.field("userPermissions")
      if (userPermissions == null) {
        userPermissions = new ArrayList[ODocument]()
      }
      userPermissions.add(0, collectionPermissionsDoc)
      collection.field("userPermissions", userPermissions)
      collection.save()
    }
    ()
  }

  def removeCollectionUserPermissions(collectionId: String, username: String): Try[Unit] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val collectionDoc = collectionRID.getRecord[ODocument]
    val userRID = DomainUserStore.getUserRid(username, db).get
    val userPermissions: JavaList[ODocument] = collectionDoc.field("userPermissions", OType.LINKLIST)

    val newPermissions = userPermissions.asScala.filterNot { permDoc =>
      if (permDoc.field("user").asInstanceOf[ODocument].getIdentity == userRID) {
        permDoc.delete()
        true
      } else {
        false
      }
    }
    collectionDoc.field("userPermissions", newPermissions.asJavaCollection)
  }

  def getModelWorldPermissions(modelFqn: ModelFqn): Try[Option[ModelPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT worldPermissions
        |  FROM Model
        |  WHERE id = :modelId AND
        |    collection.id = :collectionId""".stripMargin
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db)
    result.map { docToWorldPermissions(_) }.get
  }

  def setModelWorldPermissions(modelFqn: ModelFqn, permissions: Option[ModelPermissions]): Try[Unit] = tryWithDb { db =>
    val modelDoc = getModelRid(modelFqn).get.getRecord[ODocument]
    val permissionsDoc = permissions.map { modelPermissionToDoc(_) }
    modelDoc.fields(Fields.World, permissionsDoc.getOrElse(null)).save()
  }

  def getAllModelUserPermissions(modelFqn: ModelFqn): Try[Map[String, ModelPermissions]] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    val userPermissions: JavaList[ODocument] = modelDoc.field("userPermissions", OType.LINKLIST)
    if (userPermissions == null) {
      Map[String, ModelPermissions]()
    } else {
      userPermissions.asScala.map { userPermission =>
        val user: ODocument = userPermission.field(Fields.User)
        val username: String = user.field(Fields.Username)
        val permissions = docToModelPermissions(userPermission.field(Fields.Permissions))
        (username -> permissions)
      }.toMap
    }
  }

  def deleteAllModelUserPermissions(modelFqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    modelDoc.field("userPermissions", new ArrayList[ODocument]())
    modelDoc.save()

    val queryString =
      """DELETE FROM ModelUserPermissions
        |  WHERE model.id = :modelId AND
        |    model.collection.id = :collectionId""".stripMargin
    val command = new OCommandSQL(queryString)
    val params = Map("modelId" -> modelFqn.modelId, "collectionId" -> modelFqn.collectionId)
    db.command(command).execute(params.asJava)
  }

  def updateAllModelUserPermissions(modelFqn: ModelFqn, userPermissions: Map[String, Option[ModelPermissions]]): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get

    userPermissions.foreach {
      case (username, permissions) =>
        val userRID = DomainUserStore.getUserRid(username, db).get
        val key = new OCompositeKey(List(userRID, modelRID).asJava)
        val modelPermissionRID = getModelUserPermissionsRid(modelFqn, username)
        if (modelPermissionRID.isSuccess) {
          db.delete(modelPermissionRID.get)
        }

        permissions.foreach { perm =>
          val modelPermissionsDoc = db.newInstance(ModelUserPermissionsClass)
          modelPermissionsDoc.field(Fields.Model, modelRID)
          modelPermissionsDoc.field(Fields.User, userRID)
          modelPermissionsDoc.field(Fields.Permissions, modelPermissionToDoc(perm))
          modelPermissionsDoc.save()
        }
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
    result.map { doc => docToModelPermissions(doc.field("permissions")) }
  }

  def updateModelUserPermissions(modelFqn: ModelFqn, username: String, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, modelRID).asJava)
    val modelPermissionRID = getModelUserPermissionsRid(modelFqn, username)
    if (modelPermissionRID.isSuccess) {
      val modelPermissionsDoc = modelPermissionRID.get.getRecord[ODocument]
      modelPermissionsDoc.field(Fields.Permissions, modelPermissionToDoc(permissions))
      modelPermissionsDoc.save()
    } else {
      var modelPermissionsDoc = db.newInstance(ModelUserPermissionsClass)
      modelPermissionsDoc.field(Fields.Model, modelRID)
      modelPermissionsDoc.field(Fields.User, userRID)
      modelPermissionsDoc.field(Fields.Permissions, modelPermissionToDoc(permissions))
      modelPermissionsDoc = modelPermissionsDoc.save()

      val model = modelRID.getRecord[ODocument]
      var userPermissions: JavaList[ODocument] = model.field("userPermissions")
      if (userPermissions == null) {
        userPermissions = new ArrayList[ODocument]()
      }
      userPermissions.add(0, modelPermissionsDoc)
      model.field("userPermissions", userPermissions)
      model.save()
    }
    ()
  }

  def removeModelUserPermissions(modelFqn: ModelFqn, username: String): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    val userRID = DomainUserStore.getUserRid(username, db).get
    val userPermissions: JavaList[ODocument] = modelDoc.field("userPermissions", OType.LINKLIST)

    val newPermissions = userPermissions.asScala.filterNot { permDoc =>
      if (permDoc.field("user").asInstanceOf[ODocument].getIdentity == userRID) {
        permDoc.delete()
        true
      } else {
        false
      }
    }
    modelDoc.field("userPermissions", newPermissions.asJavaCollection)
  }

  def getCollectionRid(collectionId: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(CollectionIndex, collectionId, db).get
  }

  def getCollectionUserPermissionsRid(collectionId: String, username: String): Try[ORID] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, collectionRID).asJava)
    QueryUtil.getRidFromIndex(CollectionUserPermissionsIndex, key, db).get
  }

  def getModelRid(modelFqn: ModelFqn): Try[ORID] = tryWithDb { db =>
    val ModelFqn(collectionId, modelId) = modelFqn
    val collectionRID = getCollectionRid(collectionId).get
    val key = new OCompositeKey(List(collectionRID, modelId).asJava)
    QueryUtil.getRidFromIndex(ModelIndex, key, db).get
  }

  def getModelUserPermissionsRid(modelFqn: ModelFqn, username: String): Try[ORID] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(modelFqn.modelId, modelFqn.collectionId, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, modelRID).asJava)
    QueryUtil.getRidFromIndex(ModelUserPermissionsIndex, key, db).get
  }
}
