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

package com.convergencelabs.convergence.server.datastore.domain

import java.util

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore._
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId}
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

sealed trait Permission {
  val permission: String
}

case class UserPermission(user: DomainUser, permission: String) extends Permission
case class GroupPermission(group: UserGroup, permission: String) extends Permission
case class WorldPermission(permission: String) extends Permission

object PermissionsStore {
  import schema.DomainSchema._

  def docToPermission(doc: ODocument): Permission = {
    if (doc.containsField(Classes.Permission.Fields.AssignedTo)) {
      val assignedTo: ODocument = doc.field(Classes.Permission.Fields.AssignedTo)
      assignedTo.getClassName match {
        case Classes.User.ClassName =>
          docToUserPermission(doc)
        case Classes.UserGroup.ClassName =>
          docToGroupPermission(doc)
        case default =>
          throw new IllegalStateException("Unsupported Permissions Assignment")
      }
    } else {
      val permission: String = doc.field(Classes.Permission.Fields.Permission)
      WorldPermission(permission)
    }
  }

  def docToWorldPermission(doc: ODocument): WorldPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    WorldPermission(permission)
  }

  def docToGroupPermission(doc: ODocument): GroupPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    val assignedTo: ODocument = doc.field(Classes.Permission.Fields.AssignedTo)
    val group: UserGroup = UserGroupStore.docToGroup(assignedTo)
    GroupPermission(group, permission)
  }

  def docToUserPermission(doc: ODocument): UserPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    val assignedTo: ODocument = doc.field(Classes.Permission.Fields.AssignedTo)
    val user: DomainUser = DomainUserStore.docToDomainUser(assignedTo)
    UserPermission(user, permission)
  }
}

class PermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import schema.DomainSchema._

  def hasPermission(userId: DomainUserId, permission: String): Try[Boolean] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRID =>
      val query =
        """SELECT count(*) as count
        |  FROM Permission
        |  WHERE
        |    permission = :permission AND 
        |    not(forRecord is DEFINED) AND
        |    (
        |      not(assignedTo is DEFINED) OR
        |      assignedTo = :user OR
        |      (assignedTo.@class = 'UserGroup' AND assignedTo.members CONTAINS :user)
        |    )""".stripMargin
      val params = Map("user" -> userRID, "permission" -> permission)
      OrientDBUtil
        .getDocument(db, query, params)
        .map { doc =>
          val count: Long = doc.getProperty("count")
          count > 0
        }
    }
  }

  def hasPermission(userId: DomainUserId, forRecord: ORID, permission: String): Try[Boolean] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRID =>
      // There are three conditions that must be matched in order to find permissions
      // that allow this action to happen:
      //   1. We must match the permission exactly
      //   2. We must match permissions with this specific forRecord and permissions
      //      that don't have a forRecord defined, since those are global permissions
      //      that apply to all records that permission applies to.
      //   3. We much permissions that don't have an assignedTo field since those are
      //      world permissions. If there is an assignedTo value then the assigned to
      //      value can be this users, or a group this user belongs to.

      val query =
        """SELECT count(*) as count
        |  FROM Permission
        |  WHERE 
        |    permission = :permission AND
        |    (not(forRecord IS DEFINED) OR forRecord = :forRecord) AND
        |    (
        |      not(assignedTo IS DEFINED) OR
        |      assignedTo = :user OR
        |      (assignedTo.@class = 'UserGroup' AND assignedTo.members CONTAINS :user)
        |    )""".stripMargin
      val params = Map("user" -> userRID, "forRecord" -> forRecord, "permission" -> permission)
      OrientDBUtil
        .getDocument(db, query, params)
        .map(doc => doc.getProperty("count").asInstanceOf[Long] > 0)
    }
  }

  def permissionExists(permission: String, assignedTo: Option[ORID], forRecord: Option[ORID]): Try[Boolean] = withDb { db =>
    var params = Map[String, Any]("permission" -> permission)

    val sb = new StringBuilder
    sb.append("SELECT count(*) as count FROM Permission WHERE permission = :permission ")

    assignedTo.foreach { assignedTo =>
      sb.append("AND assignedTo = :assignedTo ")
      params += Classes.Permission.Fields.AssignedTo -> assignedTo
    }

    forRecord.foreach { forRecord =>
      sb.append("AND forRecord = :forRecord")
      params += Classes.Permission.Fields.ForRecord -> forRecord
    }

    val query = sb.toString()
    OrientDBUtil
      .getDocument(db, query, params)
      .map { doc =>
        val count: Long = doc.getProperty("count")
        count > 0
      }
  }

  def getAggregateUserPermissions(userId: DomainUserId, forRecord: ORID, forPermissions: Set[String]): Try[Set[String]] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRID =>
      val query =
        """SELECT permission
        |  FROM Permission
        |  WHERE forRecord = :forRecord AND
        |    permission in :permissions AND
        |    (
        |      not(assignedTo IS DEFINED) OR
        |      assignedTo = :user OR
        |      (assignedTo.@class = 'UserGroup' AND assignedTo.members CONTAINS :user)
        |    )""".stripMargin
      val params = Map("user" -> userRID, "forRecord" -> forRecord, "permissions" -> forPermissions)
      OrientDBUtil
        .queryAndMap(db, query, params)(_.getProperty(Classes.Permission.Fields.Permission).asInstanceOf[String])
        .map(_.toSet)
    }
  }

  def addWorldPermissions(permissions: Set[String], forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    Try(permissions.map { permission =>
      val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
      doc.field(Classes.Permission.Fields.Permission, permission)
      forRecord.foreach(doc.field(Classes.Permission.Fields.ForRecord, _))
      doc.save().getIdentity
    }).flatMap(permissionRids =>
      forRecord match {
        case Some(fr) => addPermissionsToSet(fr, permissionRids)
        case None => Success(())
      })
  }

  def addUserPermissions(permissions: Set[String], userId: DomainUserId, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRid =>
      Try(permissions.map { permission =>
        val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
        doc.field(Classes.Permission.Fields.Permission, permission)
        doc.field(Classes.Permission.Fields.AssignedTo, userRid)
        forRecord.foreach(doc.field(Classes.Permission.Fields.ForRecord, _))
        doc.save().getIdentity
      }).flatMap(permissionRids =>
        forRecord match {
          case Some(fr) => addPermissionsToSet(fr, permissionRids)
          case None => Success(())
        })
    }
  }

  def addGroupPermissions(permissions: Set[String], groupId: String, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    UserGroupStore.getGroupRid(groupId, db).flatMap { groupRid =>
      Try(permissions.map { permission =>
        val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
        doc.setProperty(Classes.Permission.Fields.Permission, permission)
        doc.setProperty(Classes.Permission.Fields.AssignedTo, groupRid)
        forRecord.foreach(doc.setProperty(Classes.Permission.Fields.ForRecord, _))
        doc.save().getIdentity
      }).flatMap(permissionRids =>
        forRecord match {
          case Some(fr) => addPermissionsToSet(fr, permissionRids)
          case None => Success(())
        })
    }
  }

  def removeWorldPermissions(permissions: Set[String], forRecord: Option[ORID]): Try[Unit] =
    removePermissions(permissions, None, forRecord)

  def removeUserPermissions(permissions: Set[String], userId: DomainUserId, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRid =>
      removePermissions(permissions, Some(userRid), forRecord)
    }
  }

  def removeGroupPermissions(permissions: Set[String], groupId: String, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    UserGroupStore.getGroupRid(groupId, db).flatMap { groupRid =>
      removePermissions(permissions, Some(groupRid), forRecord)
    }
  }

  def removePermissions(permissions: Set[String], assignedTo: Option[ORID], forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    Try(permissions.map(getPermissionRid(_, assignedTo, forRecord).get)).flatMap { permissionRids =>
      (forRecord match {
        case Some(fr) =>
          Try {
            val forDoc = fr.getRecord[ODocument]
            val permissions: util.Set[ORID] = forDoc.field(Classes.Permission.Fields.Permissions)
            permissions.removeAll(permissions)
            forDoc.field(Classes.Permission.Fields.Permissions, permissions)
            forDoc.save()
          }
        case None => Success(())
      }).flatMap { _ =>
        Try {
          permissionRids foreach db.delete
        }
      }
    }
  }

  def setWorldPermissions(permissions: Set[String], forRecord: Option[ORID]): Try[Unit] =
    removePermissions(permissions: Set[String], None, forRecord)
      .flatMap(_ => addWorldPermissions(permissions, forRecord))

  def setUserPermissions(permissions: Set[String], userId: DomainUserId, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    DomainUserStore.getUserRid(userId, db)
      .flatMap(userRid => removePermissions(permissions: Set[String], Some(userRid), forRecord))
      .flatMap(_ => addUserPermissions(permissions, userId, forRecord))
  }

  def setGroupPermissions(permissions: Set[String], groupId: String, forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    UserGroupStore.getGroupRid(groupId, db)
      .flatMap(groupRid => removePermissions(permissions: Set[String], Some(groupRid), forRecord))
      .flatMap(_ => addGroupPermissions(permissions, groupId, forRecord))
  }

  def getWorldPermissions(forRecord: Option[ORID]): Try[Set[WorldPermission]] = withDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT permission FROM Permission WHERE not(assignedTo is DEFINED) AND ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(docToWorldPermission)
      .map(_.toSet)
  }

  def getAllGroupPermissions(forRecord: Option[ORID]): Try[Set[GroupPermission]] = withDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE assignedTo is DEFINED AND assignedTo.@class = 'UserGroup' AND ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(docToGroupPermission)
      .map(_.toSet)
  }

  def getAllUserPermissions(forRecord: Option[ORID]): Try[Set[UserPermission]] = withDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE (assignedTo is DEFINED) AND (assignedTo.@class = 'User') AND ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(docToUserPermission)
      .map(_.toSet)
  }

  def getGroupPermissions(groupId: String, forRecord: Option[ORID]): Try[Set[String]] = withDb { db =>
    val groupRid = UserGroupStore.getGroupRid(groupId, db).get

    var params = Map[String, Any]("group" -> groupRid)

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE assignedTo = :group AND ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(_.field(Classes.Permission.Fields.Permission).asInstanceOf[String])
      .map(_.toSet)
  }

  def getUserPermissions(userId: DomainUserId, forRecord: Option[ORID]): Try[Set[String]] = withDb { db =>
    val userRID = DomainUserStore.getUserRid(userId, db).get

    var params = Map[String, Any]("user" -> userRID)

    val sb = new StringBuilder
    sb.append("SELECT permission FROM Permission WHERE assignedTo = :user AND ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(_.field(Classes.Permission.Fields.Permission).asInstanceOf[String])
      .map(_.toSet)
  }

  def getAllPermissions(forRecord: Option[ORID]): Try[Set[Permission]] = withDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE ")
    params = addOptionFieldParam(sb, params, Classes.Permission.Fields.ForRecord, forRecord)

    OrientDBUtil
      .queryAndMap(db, sb.toString(), params)(docToPermission)
      .map(_.toSet)
  }

  private[this] def addPermissionsToSet(forRecord: ORID, permissions: Set[ORID]): Try[Unit] = tryWithDb { db =>
    val forDoc = forRecord.getRecord[ODocument]
    val existingPermissions = Option(forDoc.getProperty(Classes.Permission.Fields.Permissions).asInstanceOf[util.Set[ORID]])
      .getOrElse(new util.HashSet[ORID].asInstanceOf[util.Set[ORID]])
    permissions.foreach(existingPermissions.add)
    forDoc.setProperty(Classes.Permission.Fields.Permissions, existingPermissions)
    forDoc.save()
    ()
  }

  def getPermissionRid(permission: String, assignedTo: Option[ORID], forRecord: Option[ORID]): Try[ORID] = withDb { db =>
    val assignedToRID = assignedTo.orNull
    val forRecordRID = forRecord.orNull
    OrientDBUtil.getIdentityFromSingleValueIndex(
      db,
      Classes.Permission.Indices.AssignedTo_ForRecord_Permission,
      List(assignedToRID, forRecordRID, permission))
  }

  private[this] def addOptionFieldParam(sb: StringBuilder, params: Map[String, Any], field: String, rid: Option[ORID]): Map[String, Any] = {
    var vParams = params
    rid match {
      case Some(rid) =>
        sb.append(s"($field = :$field)")
        vParams += field -> rid
      case None =>
        sb.append(s"not($field is DEFINED)")
    }
    vParams
  }
}
