/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.collection

import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema.Classes
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.collection.CollectionPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import java.util
import java.util.{List => JavaList}
import scala.jdk.CollectionConverters._
import scala.util.Try

class CollectionPermissionsStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import CollectionPermissionsStore._
  import schema.DomainSchema._

  private[this] val CollectionWordPermissionsQuery = "SELECT worldPermissions FROM Collection WHERE id = :collectionId"

  def getCollectionWorldPermissions(collectionId: String): Try[CollectionPermissions] = withDb { db =>
    val params = Map("collectionId" -> collectionId)
    OrientDBUtil
      .getDocument(db, CollectionWordPermissionsQuery, params)
      .map(collectionDocToCollectionWorldPermissions)
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

  def getAllCollectionUserPermissions(collectionId: String): Try[Map[DomainUserId, CollectionPermissions]] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)
      .map { collectionDoc =>
        val userPermissions: JavaList[ODocument] = collectionDoc.getProperty(Classes.Collection.Fields.UserPermissions)
        Option(userPermissions).map { userPermissions =>
          userPermissions.asScala.map { userPermission =>
            val user: ODocument = userPermission.getProperty(Classes.CollectionUserPermissions.Fields.User)
            val userId = DomainUserStore.userDocToDomainUserId(user)
            val permissionsDoc: ODocument = userPermission.getProperty(Classes.CollectionUserPermissions.Fields.Permissions)
            val permissions = docToCollectionPermissions(permissionsDoc)
            userId -> permissions
          }.toMap
        }.getOrElse(Map[DomainUserId, CollectionPermissions]())
      }
  }

  def deleteAllCollectionUserPermissions(collectionId: String): Try[Unit] = tryWithDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Classes.Collection.Indices.Id, collectionId)
      .flatMap { collectionDoc =>
        Try {
          collectionDoc.field(Classes.Collection.Fields.UserPermissions, new util.ArrayList[ODocument]())
          collectionDoc.save()
          ()
        }
      }
      .flatMap { _ =>
        val command = "DELETE FROM CollectionUserPermissions WHERE collection.id = :collectionId"
        val params = Map("collectionId" -> collectionId)
        OrientDBUtil.commandReturningCount(db, command, params)
      }
  }

  def updateAllCollectionUserPermissions(collectionId: String, userPermissions: Map[DomainUserId, Option[CollectionPermissions]]): Try[Unit] = withDb { db =>
    CollectionStore.getCollectionRid(collectionId, db).flatMap { collectionRid =>
      Try(userPermissions.map {
        case (userId, permissions) =>
          for {
            userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
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
          } yield result
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

  def getCollectionPermissionsForUser(collectionId: String, userId: DomainUserId, db: Option[ODatabaseDocument] = None): Try[Option[CollectionPermissions]] = withDb(db) { db =>
    val query =
      """SELECT permissions
        |  FROM CollectionUserPermissions
        |  WHERE collection.id = :collectionId AND
        |    user.username = :username AND
        |    user.userType = :userType""".stripMargin
    val params = Map("collectionId" -> collectionId, "username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(doc => docToCollectionPermissions(doc.getProperty("permissions"))))
  }

  def updateCollectionUserPermissions(collectionId: String, userId: DomainUserId, permissions: CollectionPermissions): Try[Unit] = withDb { db =>
    for {
      collectionRid <- CollectionStore.getCollectionRid(collectionId, db)
      userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
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
                .getOrElse(new util.ArrayList[ODocument]().asInstanceOf[JavaList[ODocument]])
            userPermissions.add(0, newPermissions)
            collection.setProperty(Classes.Collection.Fields.UserPermissions, userPermissions)
            collection.save()
            ()
        }
      }
    } yield result
  }

  def removeCollectionUserPermissions(collectionId: String, userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    for {
      collectionRid <- CollectionStore.getCollectionRid(collectionId, db)
      collection <- Try(collectionRid.getRecord[ODocument])
      userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
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
        collection.setProperty(Classes.Collection.Fields.UserPermissions, new util.ArrayList(newPermissions.asJavaCollection))
        collection.save()
        ()
      }
    } yield result
  }

  // TODO make this a script
  private[this] val RemoveCollectionUserPermissionsCommand = "UPDATE Collection REMOVE userPermissions = userPermissions[user = :user]"
  private[this] val RemoveAllCollectionPermissionsForUserCommand = "DELETE FROM CollectionUserPermissions WHERE user = :user"

  def removeAllCollectionPermissionsForUser(userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    for {
      userRid <- DomainUserStore.getUserRid(userId.username, userId.userType, db)
      _ <- OrientDBUtil.command(db, RemoveCollectionUserPermissionsCommand, Map("user" -> userRid))
      _ <- OrientDBUtil.command(db, RemoveAllCollectionPermissionsForUserCommand, Map("user" -> userRid))
    } yield ()
  }
}

private[domain] object CollectionPermissionsStore {
  def collectionDocToCollectionWorldPermissions(doc: ODocument): CollectionPermissions = {
    val worldDoc: ODocument = doc.field(Classes.Collection.Fields.WorldPermissions)
    docToCollectionPermissions(worldDoc)

  }

  def docToCollectionPermissions(doc: ODocument): CollectionPermissions = {
    CollectionPermissions(
      doc.getProperty(Classes.CollectionPermissions.Fields.Create),
      doc.getProperty(Classes.CollectionPermissions.Fields.Read),
      doc.getProperty(Classes.CollectionPermissions.Fields.Write),
      doc.getProperty(Classes.CollectionPermissions.Fields.Remove),
      doc.getProperty(Classes.CollectionPermissions.Fields.Manage))
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
}



