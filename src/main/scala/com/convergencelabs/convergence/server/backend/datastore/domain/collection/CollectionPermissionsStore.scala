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
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import java.util.{List => JavaList}
import scala.jdk.CollectionConverters._
import scala.util.Try

class CollectionPermissionsStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import CollectionPermissionsStore._
  import schema.DomainSchema._

  private[this] val GetCollectionWordPermissionsQuery =
    "SELECT worldPermissions FROM Collection WHERE id = :collectionId"

  def getCollectionWorldPermissions(collectionId: String): Try[CollectionPermissions] = withDb { db =>
    val params = Map("collectionId" -> collectionId)
    OrientDBUtil
      .getDocument(db, GetCollectionWordPermissionsQuery, params)
      .map(collectionDocToCollectionWorldPermissions)
  }

  private[this] val SetCollectionWordPermissionsCommand =
    "UPDATE Collection SET worldPermissions = :worldPermissions WHERE id = :collectionId"

  def setCollectionWorldPermissions(collectionId: String, permissions: CollectionPermissions): Try[Unit] = withDb { db =>
    val params = Map(
      "collectionId" -> collectionId,
      "worldPermissions" -> collectionPermissionToDoc(permissions)
    )
    OrientDBUtil.mutateOneDocument(db, SetCollectionWordPermissionsCommand, params)
  }

  private[this] val GetPermissionsForCollectionsQuery =
    "SELECT FROM CollectionUserPermissions WHERE collection.id IN :collectionIds"

  def getUserPermissionsForCollections(collectionIds: List[String]): Try[Map[String, Map[DomainUserId, CollectionPermissions]]] = withDb { db =>
    OrientDBUtil
      .queryAndMap(db, GetPermissionsForCollectionsQuery, Map("collectionIds" -> collectionIds.asJava)) { doc =>
        val collectionId = doc.eval("collection.id").asInstanceOf[String]
        val userId = DomainUserStore.userDocToDomainUserId(doc.getProperty(Classes.CollectionUserPermissions.Fields.User))
        val permissions = docToCollectionPermissions(doc.getProperty(Classes.CollectionUserPermissions.Fields.Permissions))
        (collectionId, (userId, permissions))
      }
      .map(_
        .groupBy(v => v._1)
        .view
        .mapValues(_.map(_._2).toMap).toMap
      )
  }

  def getUserPermissionsForCollection(collectionId: String): Try[Map[DomainUserId, CollectionPermissions]] = withDb { db =>
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

  def removeUserPermissionsForCollection(collectionId: String): Try[Unit] = tryWithDb { db =>
    val command = "DELETE FROM CollectionUserPermissions WHERE collection.id = :collectionId"
    val params = Map("collectionId" -> collectionId)
    for {
      _ <- OrientDBUtil.commandReturningCount(db, command, params)
      _ <- refreshCollectionPermissions(db, collectionId)
    } yield ()
  }

  val GetUserPermissionsForCollectionQuery = "SELECT * FROM CollectionUserPermissions WHERE collection.id = :collectionId"

  def setUserPermissionsForCollection(collectionId: String, userPermissions: Map[DomainUserId, CollectionPermissions]): Try[Unit] = withDb { db =>
    for {
      permissionDocs <- OrientDBUtil.query(db, GetUserPermissionsForCollectionQuery, Map("collectionId" -> collectionId))
      _ <- Try {
        val permissionDocsByUserId = permissionDocs.map { doc =>
          val userId = DomainUserStore.userDocToDomainUserId(doc.getProperty(Classes.CollectionUserPermissions.Fields.User))
          (userId, doc)
        }.toMap

        val allUserIds = userPermissions.keySet ++ permissionDocsByUserId.keySet
        allUserIds.foreach { userId =>
          (userPermissions.get(userId), permissionDocsByUserId.get(userId)) match {
            case (Some(permissions), Some(permissionsDoc)) =>
              permissionsDoc.setProperty(
                Classes.CollectionUserPermissions.Fields.Permissions, collectionPermissionToDoc(permissions))
              permissionsDoc.save()
            case (None, Some(doc)) =>
              doc.delete()
            case (Some(permissions), None) =>
              createOrUpdateUserPermissions(db, collectionId, userId, permissions).get
            case (None, None) =>
            // Nothing to do because there are no permissions and we don't
            // want any.
          }
        }
      }
      _ <- refreshCollectionPermissions(db, collectionId)
    } yield {}
  }

  private[this] val GetCollectionPermissionsForUserQuery =
    """SELECT permissions
      |  FROM CollectionUserPermissions
      |  WHERE
      |    collection.id = :collectionId AND
      |    user.username = :username AND
      |    user.userType = :userType""".stripMargin

  def getCollectionPermissionsForUser(collectionId: String, userId: DomainUserId, db: Option[ODatabaseDocument] = None): Try[Option[CollectionPermissions]] = withDb(db) { db =>
    val params = Map("collectionId" -> collectionId, "username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil
      .findDocument(db, GetCollectionPermissionsForUserQuery, params)
      .map(_.map(doc => docToCollectionPermissions(doc.getProperty("permissions"))))
  }

  def setCollectionPermissionsForUser(collectionId: String, userId: DomainUserId, permissions: CollectionPermissions): Try[Unit] = withDb { db =>
    for {
      _ <- createOrUpdateUserPermissions(db, collectionId, userId, permissions)
      _ <-refreshCollectionPermissions(db, collectionId)
    } yield ()
  }

  private[this] val RemoveCollectionPermissionsForUserCommand =
    """
      |DELETE FROM
      |  CollectionUserPermissions
      |WHERE
      |  collection.id = :collectionId AND
      |  user.username = :username AND
      |  user.userType = :userType""".stripMargin

  def removeCollectionPermissionsForUser(collectionId: String, userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    for {
      _ <- OrientDBUtil.command(db, RemoveCollectionPermissionsForUserCommand,
        Map("collectionId" -> collectionId, "username" -> userId.username, "userType" -> userId.userType.toString))
      _ <-refreshCollectionPermissions(db, collectionId)
    } yield ()
  }


  private[this] val RemoveAllCollectionPermissionsForUserScript =
    """
      |UPDATE Collection REMOVE userPermissions = userPermissions[user.username = :username AND user.userType = :userType];
      |DELETE FROM CollectionUserPermissions WHERE user.username = :username AND user.userType = :userType;
      |""".stripMargin

  def removeAllCollectionPermissionsForUser(userId: DomainUserId): Try[Unit] = tryWithDb { db =>
    OrientDBUtil.execute(db, RemoveAllCollectionPermissionsForUserScript,
      Map("username" -> userId.username, "userType" -> userId.userType.toString))

  }

  private[this] def refreshCollectionPermissions(db: ODatabaseDocument, collectionId: String): Try[Unit] = {
    val command =
      """UPDATE Collection
        |  SET userPermissions = (SELECT FROM CollectionUserPermissions WHERE collection.id = :collectionId)
        |  WHERE id = :collectionId""".stripMargin

    val params = Map("collectionId" -> collectionId)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  private[this] val UpsertUserPermissionsCommand =
    """
      |UPDATE
      |  CollectionUserPermissions
      |SET
      |  collection = (SELECT FROM Collection WHERE id = :collectionId),
      |  user = (SELECT FROM User WHERE userType = :userType AND username = :username),
      |  permissions = :permissions
      |UPSERT WHERE
      |  collection.id = :collectionId AND
      |  user.username = :username AND
      |  user.userType = :userType
      |""".stripMargin

  private[this] def createOrUpdateUserPermissions(db: ODatabaseDocument, collectionId: String, userId: DomainUserId, permissions: CollectionPermissions): Try[Unit] = {
    val params = Map(
      "collectionId" -> collectionId,
      "userType" -> userId.userType.toString,
      "username" -> userId.username,
      "permissions" -> collectionPermissionToDoc(permissions)
    )
    OrientDBUtil.mutateOneDocument(db, UpsertUserPermissionsCommand, params)
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
