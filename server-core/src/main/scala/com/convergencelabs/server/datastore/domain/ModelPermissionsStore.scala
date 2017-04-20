package com.convergencelabs.server.datastore.domain

import java.util.ArrayList
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.CollectionIndex
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.CollectionUserPermissionsClass
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.CollectionUserPermissionsIndex
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.Fields
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.ModelIndex
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.ModelUserPermissionsClass
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.ModelUserPermissionsIndex
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.collectionPermissionToDoc
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.docToCollectionPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.docToCollectionWorldPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.docToModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.docToWorldPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore.modelPermissionToDoc
import com.orientechnologies.orient.core.db.record.OTrackedList
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.EntityNotFoundException

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
  val ModelIndex = "Model.id"
  val UsernameIndex = "User.username"
  val CollectionUserPermissionsIndex = "CollectionUserPermissions.user_collection"
  val ModelUserPermissionsIndex = "ModelUserPermissions.user_model"

  object Fields {
    val Collection = "collection"
    val Model = "model"
    val User = "user"
    val Permissions = "permissions"

    val ID = "id"
    val OverridePermissions = "overridePermissions"
    val World = "worldPermissions"
    val UserPermissions = "userPermissions"

    val Username = "username"

    val Read = "read"
    val Write = "write"
    val Remove = "remove"
    val Manage = "manage"

    val Create = "create"
  }

  def docToCollectionWorldPermissions(doc: ODocument): CollectionPermissions = {
    val worldDoc: ODocument = doc.field(Fields.World)
    CollectionPermissions(
      worldDoc.field(Fields.Create),
      worldDoc.field(Fields.Read),
      worldDoc.field(Fields.Write),
      worldDoc.field(Fields.Remove),
      worldDoc.field(Fields.Manage))
  }

  def docToWorldPermissions(doc: ODocument): ModelPermissions = {
    val worldDoc: ODocument = doc.field(Fields.World)
    ModelPermissions(
      worldDoc.field(Fields.Read),
      worldDoc.field(Fields.Write),
      worldDoc.field(Fields.Remove),
      worldDoc.field(Fields.Manage))
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

  def getUsersCurrentModelPermissions(modelId: String, username: String): Try[ModelPermissions] = tryWithDb { db =>
    val modelRID = getModelRid(modelId).get
    val modelDoc = modelRID.getRecord[ODocument]
    val overridesPermissions: Boolean = modelDoc.field(Fields.OverridePermissions)
    if (overridesPermissions) {
      getModelUserPermissions(modelId, username).flatMap { userPerms =>
        userPerms match {
          case Some(p) =>
            Success(p)
          case None =>
            getModelWorldPermissions(modelId)
        }
      }.get
    } else {
      val collectionDoc = modelDoc.field(Fields.Collection)
      val collectionId: String = modelDoc.field(Fields.ID)
      getCollectionUserPermissions(collectionId, username).flatMap {
        userPerms =>
          userPerms match {
            case Some(p) =>
              val CollectionPermissions(create, read, write, remove, manage) = p
              Success(ModelPermissions(read, write, remove, manage))
            case None =>
              getCollectionWorldPermissions(collectionId).map { collectionWorld =>
                val CollectionPermissions(create, read, write, remove, manage) = collectionWorld
                ModelPermissions(read, write, remove, manage)
              }
          }
      }.get
    }
  }

  def getCollectionWorldPermissions(collectionId: String): Try[CollectionPermissions] = tryWithDb { db =>
    val queryString =
      """SELECT worldPermissions
        |  FROM Collection
        |  WHERE id = :collectionId""".stripMargin
    val params = Map("collectionId" -> collectionId)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db)
    result.map { docToCollectionWorldPermissions(_) }.get
  }

  def getCollectionWorldPermissionsForModel(modelId: String): Try[CollectionPermissions] = tryWithDb { db =>
    val modelDoc = this.getModelRid(modelId).get.getRecord.asInstanceOf[ODocument]
    val collectionDoc = modelDoc.field("collection", OType.LINK).asInstanceOf[ODocument];
    val world = docToCollectionWorldPermissions(collectionDoc)
    world
  }

  def setCollectionWorldPermissions(collectionId: String, permissions: CollectionPermissions): Try[Unit] = tryWithDb { db =>
    val collectionDoc = getCollectionRid(collectionId).get.getRecord[ODocument]
    val permissionsDoc = collectionPermissionToDoc(permissions)
    collectionDoc.field(Fields.World, permissionsDoc, OType.EMBEDDED)
    collectionDoc.save()
  }

  def getAllCollectionUserPermissions(collectionId: String): Try[Map[String, CollectionPermissions]] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val collectionDoc = collectionRID.getRecord[ODocument]
    val userPermissions: JavaList[ODocument] = collectionDoc.field(Fields.UserPermissions, OType.LINKLIST)
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
    collectionDoc.field(Fields.UserPermissions, new ArrayList[ODocument]())
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
        collectionPermissionRID match {
          case Success(rid) =>
            val collectionPermissionRecord = rid.getRecord[ODocument]
            permissions match {
              case Some(permissions) =>
                collectionPermissionRecord.field(Fields.Permissions, collectionPermissionToDoc(permissions))
                collectionPermissionRecord.save()
              case None => collectionPermissionRecord.delete()
            }
          case Failure(e) =>
            permissions match {
              case Some(permissions) =>
                val collectionPermissionsDoc = db.newInstance(CollectionUserPermissionsClass)
                collectionPermissionsDoc.field(Fields.Collection, collectionRID)
                collectionPermissionsDoc.field(Fields.User, userRID)
                collectionPermissionsDoc.field(Fields.Permissions, collectionPermissionToDoc(permissions))
                collectionPermissionsDoc.save()
              case None => Failure(e)
            }
        }
    }

    val queryString =
      """update Collection 
          |  set userPermissions = (select from CollectionUserPermissions 
          |                                 where  collection.id = :collectionId) 
          |  where id = :collectionId""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map("collectionId" -> collectionId)
    db.command(command).execute(params.asJava)
    ()
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
      var userPermissions: JavaList[ODocument] = collection.field(Fields.UserPermissions)
      if (userPermissions == null) {
        userPermissions = new ArrayList[ODocument]()
      }
      userPermissions.add(0, collectionPermissionsDoc)
      collection.field(Fields.UserPermissions, userPermissions)
      collection.save()
    }
    ()
  }

  def removeCollectionUserPermissions(collectionId: String, username: String): Try[Unit] = tryWithDb { db =>
    val collectionRID = CollectionStore.getCollectionRid(collectionId, db).get
    val collectionDoc = collectionRID.getRecord[ODocument]
    val userRID = DomainUserStore.getUserRid(username, db).get
    val userPermissions: JavaList[ODocument] = collectionDoc.field(Fields.UserPermissions, OType.LINKLIST)

    val newPermissions = userPermissions.asScala.filterNot { permDoc =>
      if (permDoc.field("user").asInstanceOf[ODocument].getIdentity == userRID) {
        permDoc.delete()
        true
      } else {
        false
      }
    }
    collectionDoc.field(Fields.UserPermissions, newPermissions.asJavaCollection)
  }

  def modelOverridesCollectionPermissions(id: String): Try[Boolean] = tryWithDb { db =>
    val modelDoc: ODocument = getModelRid(id).get.getRecord[ODocument]
    val overridePermissions: Boolean = modelDoc.field(Fields.OverridePermissions, OType.BOOLEAN)
    overridePermissions
  }

  def setOverrideCollectionPermissions(id: String, overridePermissions: Boolean): Try[Unit] = tryWithDb { db =>
    val modelDoc = getModelRid(id).get.getRecord[ODocument]
    modelDoc.field(Fields.OverridePermissions, overridePermissions).save()
  }

  def getModelWorldPermissions(id: String): Try[ModelPermissions] = tryWithDb { db =>
    val queryString =
      """SELECT worldPermissions
        |  FROM Model
        |  WHERE id = :modelId""".stripMargin
    val params = Map("modelId" -> id)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db)
    result.map { docToWorldPermissions(_) }.get
  }

  def setModelWorldPermissions(id: String, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    val modelDoc = getModelRid(id).get.getRecord[ODocument]
    val permissionsDoc = modelPermissionToDoc(permissions)
    modelDoc.field(Fields.World, permissionsDoc, OType.EMBEDDED)
    modelDoc.save()
  }

  def getAllModelUserPermissions(id: String): Try[Map[String, ModelPermissions]] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    val userPermissions: JavaList[ODocument] = modelDoc.field(Fields.UserPermissions, OType.LINKLIST)
    if (userPermissions == null) {
      Map[String, ModelPermissions]()
    } else {
      userPermissions.asScala.map { userPermission =>
        val user: ODocument = userPermission.field(Fields.User, OType.LINK)
        val username: String = user.field(Fields.Username)
        val permissions = docToModelPermissions(userPermission.field(Fields.Permissions))
        (username -> permissions)
      }.toMap
    }
  }

  def deleteAllModelUserPermissions(id: String): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    modelDoc.field(Fields.UserPermissions, new ArrayList[ODocument]())
    modelDoc.save()

    val queryString =
      """DELETE FROM ModelUserPermissions
        |  WHERE model.id = :modelId""".stripMargin
    val command = new OCommandSQL(queryString)
    val params = Map("modelId" -> id)
    db.command(command).execute(params.asJava)
  }

  def updateAllModelUserPermissions(id: String, userPermissions: Map[String, Option[ModelPermissions]]): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get

    userPermissions.foreach {
      case (username, permissions) =>
        val userRID = DomainUserStore.getUserRid(username, db).get
        val key = new OCompositeKey(List(userRID, modelRID).asJava)
        val modelPermissionRID =

          getModelUserPermissionsRid(id, username) map { rid =>
            val modelPermissionRecord = rid.getRecord[ODocument]
            permissions match {
              case Some(permissions) =>
                modelPermissionRecord.field(Fields.Permissions, modelPermissionToDoc(permissions))
                modelPermissionRecord.save()
              case None => modelPermissionRecord.delete()
            }
          } recover {
            case e: EntityNotFoundException =>
              permissions match {
                case Some(permissions) =>
                  val modelPermissionsDoc = db.newInstance(ModelUserPermissionsClass)
                  modelPermissionsDoc.field(Fields.Model, modelRID)
                  modelPermissionsDoc.field(Fields.User, userRID)
                  modelPermissionsDoc.field(Fields.Permissions, modelPermissionToDoc(permissions))
                  modelPermissionsDoc.save()
                case None => Failure(e)
              }
          }
    }

    val queryString =
      """update Model 
          |  set userPermissions = (select from ModelUserPermissions 
          |                                 where model.id = :modelId) 
          |  where id = :modelId and collection.id = :collectionId""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map("modelId" -> id)
    db.command(command).execute(params.asJava)
    ()
  }

  def getModelUserPermissions(id: String, username: String): Try[Option[ModelPermissions]] = tryWithDb { db =>
    val queryString =
      """SELECT permissions
        |  FROM ModelUserPermissions
        |  WHERE model.id = :modelId AND
        |    user.username = :username""".stripMargin
    val params = Map("modelId" -> id, "username" -> username)
    val result = QueryUtil.lookupOptionalDocument(queryString, params, db)
    result.map { doc => docToModelPermissions(doc.field("permissions")) }
  }

  def updateModelUserPermissions(id: String, username: String, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, modelRID).asJava)
    val modelPermissionRID = getModelUserPermissionsRid(id, username)
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
      var userPermissions: JavaList[ODocument] = model.field(Fields.UserPermissions)
      if (userPermissions == null) {
        userPermissions = new ArrayList[ODocument]()
      }
      userPermissions.add(0, modelPermissionsDoc)
      model.field(Fields.UserPermissions, userPermissions)
      model.save()
    }
    ()
  }

  def removeModelUserPermissions(id: String, username: String): Try[Unit] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get
    val modelDoc = modelRID.getRecord[ODocument]
    val userRID = DomainUserStore.getUserRid(username, db).get
    val userPermissions: OTrackedList[ODocument] = modelDoc.field(Fields.UserPermissions, OType.LINKLIST)

    val iter = userPermissions.iterator()
    var permDoc: Option[ODocument] = None
    while (iter.hasNext()) {
      val curDoc = iter.next()
      if (curDoc.field("user").asInstanceOf[ODocument].getIdentity == userRID) {
        permDoc = Some(curDoc)
        iter.remove()
      }
    }
    modelDoc.save()
    permDoc.foreach(_.delete())
    ()
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

  def getModelRid(id: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(ModelIndex, id, db).get
  }

  def getModelUserPermissionsRid(id: String, username: String): Try[ORID] = tryWithDb { db =>
    val modelRID = ModelStore.getModelRid(id, db).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, modelRID).asJava)
    QueryUtil.getRidFromIndex(ModelUserPermissionsIndex, key, db).get
  }
}
