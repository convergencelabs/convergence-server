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
import com.convergencelabs.server.db.DatabaseProvider
import com.orientechnologies.orient.core.db.record.OTrackedList
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.ModelUserPermissions

case class ModelPermissions(read: Boolean, write: Boolean, remove: Boolean, manage: Boolean)
case class CollectionPermissions(create: Boolean, read: Boolean, write: Boolean, remove: Boolean, manage: Boolean)

case class UserRoles(username: String, roles: Set[String])

object ModelPermissionsStore {

  import schema.DomainSchema._

  def docToCollectionWorldPermissions(doc: ODocument): CollectionPermissions = {
    val worldDoc: ODocument = doc.field(Classes.Collection.Fields.WorldPermissions)
    docToCollectionPermissions(worldDoc)

  }

  def docToWorldPermissions(doc: ODocument): ModelPermissions = {
    val worldDoc: ODocument = doc.field(Classes.Model.Fields.WorldPermissions)
    docToModelPermissions(worldDoc)
  }

  def docToCollectionPermissions(doc: ODocument): CollectionPermissions = {
    CollectionPermissions(
      doc.getProperty(Classes.CollectionPermissions.Fields.Create),
      doc.getProperty(Classes.CollectionPermissions.Fields.Read),
      doc.getProperty(Classes.CollectionPermissions.Fields.Write),
      doc.getProperty(Classes.CollectionPermissions.Fields.Remove),
      doc.getProperty(Classes.CollectionPermissions.Fields.Manage))
  }

  def docToModelPermissions(doc: ODocument): ModelPermissions = {
    ModelPermissions(
      doc.getProperty(Classes.ModelPermissions.Fields.Read),
      doc.getProperty(Classes.ModelPermissions.Fields.Write),
      doc.getProperty(Classes.ModelPermissions.Fields.Remove),
      doc.getProperty(Classes.ModelPermissions.Fields.Manage))
  }

  def collectionPermissionToDoc(permissions: CollectionPermissions): ODocument = {
    val doc = new ODocument(Classes.CollectionPermissions.ClassName)
    doc.setProperty(Classes.CollectionPermissions.Fields.Create, permissions.create)
    doc.setProperty(Classes.CollectionPermissions.Fields.Read, permissions.read)
    doc.setProperty(Classes.CollectionPermissions.Fields.Write, permissions.write)
    doc.setProperty(Classes.CollectionPermissions.Fields.Remove, permissions.remove)
    doc.setProperty(Classes.CollectionPermissions.Fields.Manage, permissions.manage)
    doc
  }

  def modelPermissionToDoc(permissions: ModelPermissions): ODocument = {
    val doc = new ODocument(Classes.ModelPermissions.ClassName)
    doc.setProperty(Classes.ModelPermissions.Fields.Read, permissions.read)
    doc.setProperty(Classes.ModelPermissions.Fields.Write, permissions.write)
    doc.setProperty(Classes.ModelPermissions.Fields.Remove, permissions.remove)
    doc.setProperty(Classes.ModelPermissions.Fields.Manage, permissions.manage)
    doc
  }
}

class ModelPermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {
  import schema.DomainSchema._
  import ModelPermissionsStore._

  def getUsersCurrentModelPermissions(modelId: String, username: String): Try[ModelPermissions] = withDb { db =>
    getModelRid(db, modelId).flatMap(rid => Try(rid.getRecord[ODocument])).flatMap { modelDoc =>
      val overridesPermissions: Boolean = modelDoc.getProperty(Classes.Model.Fields.OverridePermissions)
      if (overridesPermissions) {
        getModelUserPermissions(modelId, username).flatMap { userPerms =>
          userPerms match {
            case Some(p) =>
              Success(p)
            case None =>
              getModelWorldPermissions(modelId)
          }
        }
      } else {
        val collectionDoc = modelDoc.getProperty(Classes.Model.Fields.Collection)
        val collectionId: String = modelDoc.getProperty(Classes.Model.Fields.Id)
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
        }
      }
    }
  }

  private[this] val CollectionWordPermissionsQuery = "SELECT worldPermissions FROM Collection WHERE id = :collectionId"
  def getCollectionWorldPermissions(collectionId: String): Try[CollectionPermissions] = withDb { db =>
    val params = Map("collectionId" -> collectionId)
    OrientDBUtil
      .getDocument(db, CollectionWordPermissionsQuery, params)
      .map(docToCollectionWorldPermissions(_))
  }

  def getCollectionWorldPermissionsForModel(modelId: String): Try[CollectionPermissions] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, modelId)
      .map { modelDoc =>
        val collectionDoc: ODocument = modelDoc.getProperty(Classes.Model.Fields.Collection)
        val world = docToCollectionWorldPermissions(collectionDoc)
        world
      }
  }

  def setCollectionWorldPermissions(collectionId: String, permissions: CollectionPermissions): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)
      .flatMap { collectionDoc =>
        Try {
          val permissionsDoc = collectionPermissionToDoc(permissions)
          collectionDoc.setProperty(Classes.Collection.Fields.WorldPermissions, permissionsDoc, OType.EMBEDDED)
          collectionDoc.save()
          ()
        }
      }
  }

  def getAllCollectionUserPermissions(collectionId: String): Try[Map[String, CollectionPermissions]] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)
      .map { collectionDoc =>
        val userPermissions: JavaList[ODocument] = collectionDoc.getProperty(Classes.Collection.Fields.UserPermissions)
        Option(userPermissions).map { userPermissions =>
          userPermissions.asScala.map { userPermission =>
            val user: ODocument = userPermission.getProperty(Classes.CollectionUserPermissions.Fields.User)
            val username: String = user.field(Classes.User.Fields.Username)
            val permissionsDoc: ODocument = userPermission.getProperty(Classes.CollectionUserPermissions.Fields.Permissions)
            val permissions = docToCollectionPermissions(permissionsDoc)
            (username -> permissions)
          }.toMap
        }.getOrElse(Map[String, CollectionPermissions]())
      }
  }

  def deleteAllCollectionUserPermissions(collectionId: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)
      .flatMap { collectionDoc =>
        Try {
          collectionDoc.field(Classes.Collection.Fields.UserPermissions, new ArrayList[ODocument]())
          collectionDoc.save()
          ()
        }
      }
      .flatMap { _ =>
        val command = "DELETE FROM CollectionUserPermissions WHERE collection.id = :collectionId"
        val params = Map("collectionId" -> collectionId)
        OrientDBUtil.command(db, command, params)
      }
  }

  def updateAllCollectionUserPermissions(collectionId: String, userPermissions: Map[String, Option[CollectionPermissions]]): Try[Unit] = withDb { db =>
    CollectionStore.getCollectionRid(collectionId, db).flatMap { collectionRid =>
      Try(userPermissions.map {
        case (username, permissions) =>
          for {
            userRid <- DomainUserStore.getUserRid(username, db)
            collectionPermissionRecord <- OrientDBUtil.findDocumentFromSingleValueIndex(db, Classes.CollectionUserPermissions.Indices.User_Collection, List(userRid, collectionRid))
            result <- Try {
              collectionPermissionRecord match {
                case Some(collectionPermission) =>
                  permissions match {
                    case Some(permissions) =>
                      collectionPermission.setProperty(
                        Classes.CollectionUserPermissions.Fields.Permissions, collectionPermissionToDoc(permissions))
                      collectionPermission.save()
                      ()
                    case None =>
                      collectionPermission.delete()
                      ()
                  }
                case None =>
                  permissions match {
                    case Some(permissions) =>
                      val collectionPermissionsDoc: ODocument = db.newInstance(Classes.CollectionUserPermissions.ClassName)
                      collectionPermissionsDoc.setProperty(Classes.CollectionUserPermissions.Fields.Collection, collectionRid)
                      collectionPermissionsDoc.setProperty(Classes.CollectionUserPermissions.Fields.User, userRid)
                      collectionPermissionsDoc.setProperty(Classes.CollectionUserPermissions.Fields.Permissions, collectionPermissionToDoc(permissions))
                      collectionPermissionsDoc.save()
                      ()
                    case None =>
                      // Nothing to do because there are no permissions and we were asked to delete them.
                      ()
                  }
              }
            }
          } yield (result)
      }.foreach(_.get))
    }.flatMap { _ =>
      val command =
        """UPDATE Collection 
        |  SET userPermissions = (SELECT FROM CollectionUserPermissions WHERE collection.id = :collectionId) 
        |  WHERE id = :collectionId""".stripMargin

      val params = Map("collectionId" -> collectionId)
      OrientDBUtil.mutateOneDocument(db, command, params)
    }
  }

  def getCollectionUserPermissions(collectionId: String, username: String): Try[Option[CollectionPermissions]] = withDb { db =>
    val query =
      """SELECT permissions
        |  FROM CollectionUserPermissions
        |  WHERE collection.id = :collectionId AND
        |    user.username = :username""".stripMargin
    val params = Map("collectionId" -> collectionId, "username" -> username)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(doc => docToCollectionPermissions(doc.getProperty("permissions"))))
  }

  def updateCollectionUserPermissions(collectionId: String, username: String, permissions: CollectionPermissions): Try[Unit] = withDb { db =>
    for {
      collectionRid <- CollectionStore.getCollectionRid(collectionId, db)
      userRid <- DomainUserStore.getUserRid(username, db)
      collectionUserPermissions <- OrientDBUtil
        .findDocumentFromSingleValueIndex(db, Classes.CollectionUserPermissions.Indices.User_Collection, List(userRid, collectionRid))
      result <- Try {
        collectionUserPermissions match {
          case Some(existingPermissions) =>
            existingPermissions.setProperty(Classes.CollectionUserPermissions.Fields.Permissions, collectionPermissionToDoc(permissions))
            existingPermissions.save()
            ()
          case None =>
            val newPermissions: ODocument = db.newInstance(Classes.CollectionUserPermissions.ClassName)
            newPermissions.setProperty(Classes.CollectionUserPermissions.Fields.Collection, collectionRid)
            newPermissions.setProperty(Classes.CollectionUserPermissions.Fields.User, userRid)
            newPermissions.setProperty(Classes.CollectionUserPermissions.Fields.Permissions, collectionPermissionToDoc(permissions))
            newPermissions.save()

            val collection = collectionRid.getRecord[ODocument]
            val userPermissions =
              Option(collection.getProperty(Classes.Collection.Fields.UserPermissions).asInstanceOf[JavaList[ODocument]])
                .getOrElse(new ArrayList[ODocument]().asInstanceOf[JavaList[ODocument]])
            userPermissions.add(0, newPermissions)
            collection.setProperty(Classes.Collection.Fields.UserPermissions, userPermissions)
            collection.save()
            ()
        }
      }
    } yield (result)
  }

  def removeCollectionUserPermissions(collectionId: String, username: String): Try[Unit] = tryWithDb { db =>
    for {
      collectionRid <- CollectionStore.getCollectionRid(collectionId, db)
      collection <- Try(collectionRid.getRecord[ODocument])
      userRid <- DomainUserStore.getUserRid(username, db)
      result <- Try {
        val userPermissions: JavaList[ODocument] = collection.getProperty(Classes.Collection.Fields.UserPermissions)
        val newPermissions = userPermissions.asScala.filterNot { permDoc =>
          val permUserRid = permDoc.getProperty(Classes.CollectionUserPermissions.Fields.User).asInstanceOf[ODocument].getIdentity
          if (permUserRid == userRid) {
            permDoc.delete()
            true
          } else {
            false
          }
        }
        collection.setProperty(Classes.Collection.Fields.UserPermissions, new ArrayList(newPermissions.asJavaCollection))
        collection.save()
        ()
      }
    } yield (result)
  }

  def modelOverridesCollectionPermissions(id: String): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .map(_.getProperty(Classes.Model.Fields.OverridePermissions).asInstanceOf[Boolean])
  }

  def setOverrideCollectionPermissions(id: String, overridePermissions: Boolean): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .flatMap { modelDoc =>
        Try {
          modelDoc.setProperty(Classes.Model.Fields.OverridePermissions, overridePermissions)
          modelDoc.save()
          ()
        }
      }
  }

  def getModelWorldPermissions(id: String): Try[ModelPermissions] = withDb { db =>
    val query = "SELECT worldPermissions FROM Model WHERE id = :modelId"
    val params = Map("modelId" -> id)
    OrientDBUtil
      .getDocument(db, query, params)
      .map(docToWorldPermissions(_))
  }

  def setModelWorldPermissions(id: String, permissions: ModelPermissions): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .flatMap { modelDoc =>
        Try {
          val permissionsDoc = modelPermissionToDoc(permissions)
          modelDoc.setProperty(Classes.Model.Fields.WorldPermissions, permissionsDoc, OType.EMBEDDED)
          modelDoc.save()
          ()
        }
      }
  }

  def getAllModelUserPermissions(id: String): Try[Map[String, ModelPermissions]] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .map { modelDoc =>
        Option(modelDoc.getProperty(Classes.Model.Fields.UserPermissions).asInstanceOf[JavaList[ODocument]])
          .map { userPermissions =>
            userPermissions.asScala.map { userPermission =>
              val user: ODocument = userPermission.field(Classes.ModelUserPermissions.Fields.User, OType.LINK)
              val username: String = user.field(Classes.User.Fields.Username)
              val permissions = docToModelPermissions(userPermission.field(Classes.ModelUserPermissions.Fields.Permissions))
              (username -> permissions)
            }.toMap
          }
          .getOrElse(Map[String, ModelPermissions]())
      }
  }

  def deleteAllModelUserPermissions(id: String): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .flatMap { modelDoc =>
        Try {
          modelDoc.field(Classes.Model.Fields.UserPermissions, new ArrayList[ODocument]())
          modelDoc.save()
        }
      }
      .flatMap { _ =>
        val command = "DELETE FROM ModelUserPermissions WHERE model.id = :modelId"
        val params = Map("modelId" -> id)
        OrientDBUtil.command(db, command, params).map(_ => ())
      }
  }

  def updateAllModelUserPermissions(modelId: String, userPermissions: Map[String, Option[ModelPermissions]]): Try[Unit] = tryWithDb { db =>
    ModelStore.getModelRid(modelId, db).flatMap { modelRid =>
      Try(userPermissions.map {
        case (username, permissions) =>
          for {
            userRid <- DomainUserStore.getUserRid(username, db)
            modelUserPermission <- OrientDBUtil.findDocumentFromSingleValueIndex(db, Classes.ModelUserPermissions.Indices.User_Model, List(userRid, modelRid))
            result <- Try {
              modelUserPermission match {
                case Some(existingPermissions) =>
                  permissions match {
                    case Some(permissions) =>
                      existingPermissions.setProperty(
                        Classes.ModelUserPermissions.Fields.Permissions,
                        modelPermissionToDoc(permissions),
                        OType.EMBEDDED)
                      existingPermissions.save()
                      ()
                    case None =>
                      existingPermissions.delete()
                      ()
                  }
                case None =>
                  permissions match {
                    case Some(permissions) =>
                      val newPermissions: ODocument = db.newInstance(Classes.ModelUserPermissions.ClassName)
                      newPermissions.field(Classes.ModelUserPermissions.Fields.Model, modelRid, OType.LINK)
                      newPermissions.field(Classes.ModelUserPermissions.Fields.User, userRid, OType.LINK)
                      newPermissions.field(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions), OType.EMBEDDED)
                      newPermissions.save()
                      ()
                    case None =>
                      ()
                  }
              }
            }
          } yield (result)
      }.foreach(_.get))
    }.flatMap { _ =>
      val command = "UPDATE Model SET userPermissions = (SELECT FROM ModelUserPermissions WHERE model.id = :modelId) WHERE id = :modelId"
      val params = Map("modelId" -> modelId)
      OrientDBUtil.command(db, command, params).map(_ => ())
    }
  }

  def getModelUserPermissions(modelId: String, username: String): Try[Option[ModelPermissions]] = withDb { db =>
    val query = "SELECT permissions FROM ModelUserPermissions WHERE model.id = :modelId AND user.username = :username"
    val params = Map("modelId" -> modelId, "username" -> username)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(doc => docToModelPermissions(doc.getProperty(Classes.ModelUserPermissions.Fields.Permissions).asInstanceOf[ODocument])))
  }

  def updateModelUserPermissions(modelId: String, username: String, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    for {
      modelRID <- ModelStore.getModelRid(modelId, db)
      userRID <- DomainUserStore.getUserRid(username, db)
      userModelPermissions <- OrientDBUtil.findDocumentFromSingleValueIndex(db, Classes.ModelUserPermissions.Indices.User_Model, List(userRID, modelRID))
      result <- Try {
        userModelPermissions match {
          case Some(exisitingPermissions) =>
            // This user already has permissions for this model set, so we just need to update them.
            exisitingPermissions.setProperty(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions))
            exisitingPermissions.save()
            ()
          case None =>
            // This user does not already have permissions for this model, so we need to
            // create the user permission object persist it and also add it to the model
            val newPermissions: ODocument = db.newInstance(Classes.ModelUserPermissions.ClassName)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Model, modelRID)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.User, userRID)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions))
            newPermissions.save()

            val model = modelRID.getRecord[ODocument]
            val currentPermissions = model.getProperty(Classes.Model.Fields.UserPermissions).asInstanceOf[JavaList[ODocument]]
            val userPermissions = Option(currentPermissions).getOrElse(new ArrayList[ODocument]())
            userPermissions.add(0, newPermissions)
            model.setProperty(Classes.Model.Fields.UserPermissions, userPermissions)
            model.save()
            ()
        }
      }
    } yield (result)
  }

  def removeModelUserPermissions(id: String, username: String): Try[Unit] = tryWithDb { db =>
    for {
      modelRid <- ModelStore.getModelRid(id, db)
      model <- Try(modelRid.getRecord[ODocument])
      userRid <- DomainUserStore.getUserRid(username, db)
      result <- Try {
        val userPermissions: OTrackedList[ODocument] = model.getProperty(Classes.Model.Fields.UserPermissions)
        val newPermissions = userPermissions.asScala.filterNot { permDoc =>
          val permUserRid = permDoc.getProperty(Classes.ModelUserPermissions.Fields.User).asInstanceOf[ODocument].getIdentity
          if (permUserRid == userRid) {
            permDoc.delete()
            true
          } else {
            false
          }
        }
        model.setProperty(Classes.Model.Fields.UserPermissions, new ArrayList(newPermissions.asJavaCollection))
        model.save()
        ()
      }
    } yield (result)
  }

  private[this] def getCollectionRid(db: ODatabaseDocument, collectionId: String): Try[ORID] =
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)

  private[this] def findCollectionUserPermissionsRid(db: ODatabaseDocument, collectionId: String, username: String): Try[Option[ODocument]] = {
    for {
      collectionRid <- CollectionStore.getCollectionRid(collectionId, db)
      userRid <- DomainUserStore.getUserRid(username, db)
      collectionRid <- OrientDBUtil
        .findDocumentFromSingleValueIndex(db, Classes.CollectionUserPermissions.Indices.User_Collection, List(userRid, collectionRid))
    } yield (collectionRid)
  }

  private[this] def getModelRid(db: ODatabaseDocument, modelId: String): Try[ORID] =
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Model.Indices.Id, modelId)
}
