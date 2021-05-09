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

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionPermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, EntityNotFoundException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.collection.CollectionPermissions
import com.convergencelabs.convergence.server.model.domain.model.ModelPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.{OIdentifiable, OTrackedList}
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import java.util
import java.util.{List => JavaList}
import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

class ModelPermissionsStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import ModelPermissionsStore._
  import schema.DomainSchema._

  def getCollectionWorldPermissionsForModel(modelId: String): Try[CollectionPermissions] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, modelId)
      .map { modelDoc =>
        val collectionDoc: ODocument = modelDoc.getProperty(Classes.Model.Fields.Collection)
        val world = CollectionPermissionsStore.collectionDocToCollectionWorldPermissions(collectionDoc)
        world
      }
  }

  def modelOverridesCollectionPermissions(id: String): Try[Boolean] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .map { doc =>
        val foo = doc.getProperty(Classes.Model.Fields.OverridePermissions).asInstanceOf[Boolean]
        foo
      }
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
      .map(modelDocToWorldPermissions)
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

  def getAllModelUserPermissions(id: String): Try[Map[DomainUserId, ModelPermissions]] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .map { modelDoc =>
        Option(modelDoc.getProperty(Classes.Model.Fields.UserPermissions).asInstanceOf[JavaList[ODocument]])
          .map { userPermissions =>
            userPermissions.asScala.map { userPermission =>
              val userRecord: OIdentifiable = userPermission.getProperty(Classes.ModelUserPermissions.Fields.User)
              val userId = DomainUserStore.userDocToDomainUserId(userRecord.getRecord[OElement])
              val permissions = docToModelPermissions(userPermission.getProperty(Classes.ModelUserPermissions.Fields.Permissions))
              userId -> permissions
            }.toMap
          }
          .getOrElse(Map[DomainUserId, ModelPermissions]())
      }
  }

  def deleteAllModelUserPermissions(id: String): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Model.Indices.Id, id)
      .flatMap { modelDoc =>
        Try {
          modelDoc.field(Classes.Model.Fields.UserPermissions, new util.ArrayList[ODocument]())
          modelDoc.save()
        }
      }
      .flatMap { _ =>
        val command = "DELETE FROM ModelUserPermissions WHERE model.id = :modelId"
        val params = Map("modelId" -> id)
        OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
      }
  }

  def updateAllModelUserPermissions(modelId: String, userPermissions: Map[DomainUserId, Option[ModelPermissions]]): Try[Unit] = tryWithDb { db =>
    ModelStore.getModelRid(modelId, db).flatMap { modelRid =>
      Try(userPermissions.map { case (userId, permissions) =>
        DomainUserStore.getUserRid(userId.username, userId.userType, db).flatMap { userRid =>
          for {
            modelUserPermission <-
              OrientDBUtil.findDocumentFromSingleValueIndex(db, Classes.ModelUserPermissions.Indices.User_Model, List(userRid, modelRid))
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
                      val newPermissions: OElement = db.newElement(Classes.ModelUserPermissions.ClassName)
                      newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Model, modelRid, OType.LINK)
                      newPermissions.setProperty(Classes.ModelUserPermissions.Fields.User, userRid, OType.LINK)
                      newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions), OType.EMBEDDED)
                      newPermissions.save()
                      ()
                    case None =>
                      ()
                  }
              }
            }
          } yield result
        } recoverWith {
          // This simply ignores the case where a user did not exist.
          case _: EntityNotFoundException => Success(())
        }
      }.foreach(_.get))
    }.flatMap { _ =>
      val command = "UPDATE Model SET userPermissions = (SELECT FROM ModelUserPermissions WHERE model.id = :modelId) WHERE id = :modelId"
      val params = Map("modelId" -> modelId)
      OrientDBUtil.commandReturningCount(db, command, params).map(_ => ())
    }
  }

  def getModelUserPermissions(modelId: String, userId: DomainUserId, db: Option[ODatabaseDocument] = None): Try[Option[ModelPermissions]] = withDb(db) { db =>
    val query = "SELECT permissions FROM ModelUserPermissions WHERE model.id = :modelId AND user.username = :username AND user.userType = :userType"
    val params = Map("modelId" -> modelId, "username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(doc => docToModelPermissions(doc.getProperty(Classes.ModelUserPermissions.Fields.Permissions).asInstanceOf[ODocument])))
  }

  def updateModelUserPermissions(modelId: String, userId: DomainUserId, permissions: ModelPermissions): Try[Unit] = tryWithDb { db =>
    for {
      modelRID <- ModelStore.getModelRid(modelId, db)
      userRID <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
      userModelPermissions <- OrientDBUtil.findDocumentFromSingleValueIndex(db, Classes.ModelUserPermissions.Indices.User_Model, List(userRID, modelRID))
      result <- Try {
        userModelPermissions match {
          case Some(existingPermissions) =>
            // This user already has permissions for this model set, so we just need to update them.
            existingPermissions.setProperty(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions))
            existingPermissions.save()
            ()
          case None =>
            // This user does not already have permissions for this model, so we need to
            // create the user permission object persist it and also add it to the model
            val newPermissions: OElement = db.newElement(Classes.ModelUserPermissions.ClassName)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Model, modelRID)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.User, userRID)
            newPermissions.setProperty(Classes.ModelUserPermissions.Fields.Permissions, modelPermissionToDoc(permissions))
            newPermissions.save()

            val model = modelRID.getRecord[OElement]
            val currentPermissions = model.getProperty(Classes.Model.Fields.UserPermissions).asInstanceOf[JavaList[OElement]]
            val userPermissions = Option(currentPermissions).getOrElse(new util.ArrayList[OElement]())
            userPermissions.add(0, newPermissions)
            model.setProperty(Classes.Model.Fields.UserPermissions, userPermissions)
            model.save()
            ()
        }
      }
    } yield result
  }

  // TODO make this a script
  private[this] val RemoveModelUserPermissionsCommand = "UPDATE Model REMOVE userPermissions = userPermissions[user = :user]"
  private[this] val RemoveAllModelPermissionsForUserCommand = "DELETE FROM ModelUserPermissions WHERE user = :user"

  def removeAllModelPermissionsForUser(userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    for {
      userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
      _ <- OrientDBUtil.command(db, RemoveModelUserPermissionsCommand, Map("user" -> userRid))
      _ <- OrientDBUtil.command(db, RemoveAllModelPermissionsForUserCommand, Map("user" -> userRid))
    } yield ()
  }

  def removeModelUserPermissions(id: String, userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    for {
      modelRid <- ModelStore.getModelRid(id, db)
      model <- Try(modelRid.getRecord[ODocument])
      userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
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
        model.setProperty(Classes.Model.Fields.UserPermissions, new util.ArrayList(newPermissions.asJavaCollection))
        model.save()
        ()
      }
    } yield result
  }
}


private[domain] object ModelPermissionsStore {

  import schema.DomainSchema._



  def modelDocToWorldPermissions(doc: ODocument): ModelPermissions = {
    val worldDoc: ODocument = doc.field(Classes.Model.Fields.WorldPermissions)
    docToModelPermissions(worldDoc)
  }



  def docToModelPermissions(doc: ODocument): ModelPermissions = {
    ModelPermissions(
      doc.getProperty(Classes.ModelPermissions.Fields.Read),
      doc.getProperty(Classes.ModelPermissions.Fields.Write),
      doc.getProperty(Classes.ModelPermissions.Fields.Remove),
      doc.getProperty(Classes.ModelPermissions.Fields.Manage))
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
