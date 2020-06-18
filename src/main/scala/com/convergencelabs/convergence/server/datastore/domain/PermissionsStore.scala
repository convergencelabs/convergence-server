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

import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.{resolveTarget, _}
import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging
import scala.jdk.CollectionConverters._

import scala.util.{Success, Try}

/**
 * The [PermissionStore] provides persistence for the permission subsystem.
 * Permission are scoped to Grantees which define who the permission is
 * granted to, as well as targets which define what the permission applies
 * to. Grantees can be the World (everyone), a User, or a User Group.
 * Targets can be globally scoped, or scoped to one of the entities in
 * the system such as a Chat.
 *
 * @param dbProvider The dbProvider that provides connections to the
 *                   database.
 */
class PermissionsStore private[domain](dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider) with Logging {

  import schema.DomainSchema._

  /////////////////////////////////////////////////////////////////////////////
  // Permissions Checks
  /////////////////////////////////////////////////////////////////////////////

  // TODO consider refactoring these three methods as they are very similar

  def userHasGlobalPermission(userId: DomainUserId, permission: String): Try[Boolean] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRid =>
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
      val params = Map("user" -> userRid, "permission" -> permission)
      OrientDBUtil
        .getDocument(db, query, params)
        .map { doc =>
          val count: Long = doc.getProperty("count")
          count > 0
        }
    }
  }

  def userHasPermissionForTarget(userId: DomainUserId, target: NonGlobalPermissionTarget, permission: String): Try[Boolean] = withDb { db =>
    // There are three conditions that must be matched in order to find permissions
    // that allow this action to happen:
    //   1. We must match the permission exactly
    //   2. We must match permissions with this specific forRecord and permissions
    //      that don't have a forRecord defined, since those are global permissions
    //      that apply to all records that permission applies to.
    //   3. We much permissions that don't have an assignedTo field since those are
    //      world permissions. If there is an assignedTo value then the assigned to
    //      value can be this users, or a group this user belongs to.

    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      forRecord <- resolveNonGlobalTarget(target, db)
      query =
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
      params = Map("user" -> userRid, "forRecord" -> forRecord, "permission" -> permission)
      has <- OrientDBUtil
        .getDocument(db, query, params)
        .map(doc => doc.getProperty("count").asInstanceOf[Long] > 0)
    } yield has
  }

  def getAggregateUserPermissionsForTarget(userId: DomainUserId,
                                           target: NonGlobalPermissionTarget,
                                           filter: Set[String]): Try[Set[String]] = withDb { db =>
    for {
      forRecord <- resolveNonGlobalTarget(target, db)
      userRid <- DomainUserStore.getUserRid(userId, db)
      query =
      """SELECT permission
        |  FROM Permission
        |  WHERE
        |    permission in :permissions AND
        |    (not(forRecord IS DEFINED) OR forRecord = :forRecord) AND
        |    (
        |      not(assignedTo IS DEFINED) OR
        |      assignedTo = :user OR
        |      (assignedTo.@class = 'UserGroup' AND assignedTo.members CONTAINS :user)
        |    )""".stripMargin
      params = Map("user" -> userRid, "forRecord" -> forRecord, "permissions" -> filter.asJava)
      permissions <- OrientDBUtil
        .queryAndMap(db, query, params)(_.getProperty(Classes.Permission.Fields.Permission).asInstanceOf[String])
        .map(_.toSet)
    } yield permissions
  }

  /////////////////////////////////////////////////////////////////////////////
  // World Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getPermissionsForWorld(target: PermissionTarget): Try[Set[WorldPermission]] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToWorld, forRecord, docToWorldPermission)
    } yield permissions
  }

  def addPermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- addPermissionsForWorld(db, permissions, forRecord)
    } yield ()
  }

  def removePermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- removePermissions(permissions, None, forRecord)
    } yield ()
  }

  def setPermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToWorld, forRecord)
      _ <- addPermissionsForWorld(db, permissions, forRecord)
    } yield ()
  }

  private[this] def addPermissionsForWorld(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           forRecord: Option[ORID]): Try[Unit] = {
    for {
      newPermissionRids <- Try(permissions.map { permission =>
        val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
        doc.field(Classes.Permission.Fields.Permission, permission)
        forRecord.foreach(doc.field(Classes.Permission.Fields.ForRecord, _))
        doc.save()
        doc.getIdentity
      })
    } yield {
      forRecord match {
        case Some(fr) => addPermissionsToSet(fr, newPermissionRids)
        case None => ()
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // User Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getUserPermissionsForTarget(target: PermissionTarget): Try[Set[UserPermission]] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToAnyUser, forRecord, docToUserPermission)
    } yield permissions
  }

  def getPermissionsForUser(userId: DomainUserId, target: PermissionTarget): Try[Set[String]] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      permissions <- getAllPermissionsAndMap(db, GrantedToRid(userRid), forRecord, docToPermissionString)
    } yield permissions
  }

  def addPermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- addPermissionsForUser(db, permissions, userId, forRecord)
    } yield ()
  }

  def setPermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      resolvedTarget <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), resolvedTarget)
      _ <- addPermissionsForUser(db, permissions, userId, resolvedTarget)
    } yield ()
  }

  def removePermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removePermissions(permissions, Some(userRid), forRecord)
    } yield ()
  }

  def removeAllPermissionsForUser(userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), forRecord)
    } yield ()
  }

  private[this] def addPermissionsForUser(db: ODatabaseDocument,
                                          permissions: Set[String],
                                          userId: DomainUserId,
                                          forRecord: Option[ORID]): Try[Unit] = {
    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      permissionRids <- Try(permissions.map { permission =>
        val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
        doc.field(Classes.Permission.Fields.Permission, permission)
        doc.field(Classes.Permission.Fields.AssignedTo, userRid)
        forRecord.foreach(doc.field(Classes.Permission.Fields.ForRecord, _))
        doc.save().getIdentity
      })
    } yield {
      forRecord match {
        case Some(fr) => addPermissionsToSet(fr, permissionRids)
        case None => ()
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Group Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getGroupPermissionsForTarget(target: PermissionTarget): Try[Set[GroupPermission]] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToAnyGroup, forRecord, docToGroupPermission)
    } yield permissions
  }

  def getPermissionsForGroup(groupId: String, target: PermissionTarget): Try[Set[String]] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      permissions <- getAllPermissionsAndMap(db, GrantedToRid(groupRid), forRecord, docToPermissionString)
    } yield permissions
  }

  def addPermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- addPermissionsForGroup(db, permissions, groupId, forRecord)
    } yield ()
  }

  def setPermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), forRecord)
      _ <- addPermissionsForGroup(db, permissions, groupId, forRecord)
    } yield ()
  }

  def removePermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removePermissions(permissions, Some(groupRid), forRecord)
    } yield ()
  }

  def removeAllPermissionsForGroup(groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), forRecord)
    } yield ()
  }

  private[this] def addPermissionsForGroup(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           groupId: String,
                                           forRecord: Option[ORID]): Try[Unit] = {
    for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      permissionRids <- Try(permissions.map { permission =>
        val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
        doc.setProperty(Classes.Permission.Fields.Permission, permission)
        doc.setProperty(Classes.Permission.Fields.AssignedTo, groupRid)
        forRecord.foreach(doc.setProperty(Classes.Permission.Fields.ForRecord, _))
        doc.save().getIdentity
      })
    } yield {
      forRecord match {
        case Some(fr) => addPermissionsToSet(fr, permissionRids)
        case None => ()
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Target Permissions
  /////////////////////////////////////////////////////////////////////////////

  def removeAllPermissionsForTarget(target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- removeAllPermissionsForGranteeAndTarget(db, AnyGrantee, forRecord)
    } yield ()
  }

  /////////////////////////////////////////////////////////////////////////////
  // General Helpers
  /////////////////////////////////////////////////////////////////////////////

  private[this] def docToPermissionString(doc: ODocument): String =
    doc.field(Classes.Permission.Fields.Permission).asInstanceOf[String]


  private[this] def getAllPermissionsAndMap[T](db: ODatabaseDocument,
                                               assignedTo: PermissionGrantee,
                                               forRecord: Option[ORID],
                                               mapper: ODocument => T): Try[Set[T]] = {
    getPermissionsByGranteeAndTargetRid(db, assignedTo, forRecord, mapper)
  }

  private[this] def addPermissionsToSet(forRecord: ORID, permissions: Set[ORID]): Unit = {
    val forDoc = forRecord.getRecord[ODocument]
    val existingPermissions = Option(forDoc.getProperty(Classes.Permission.Fields.Permissions).asInstanceOf[util.Set[ORID]])
      .getOrElse(new util.HashSet[ORID].asInstanceOf[util.Set[ORID]])
    val newPermissions = new util.HashSet[ORID]()
    newPermissions.addAll(existingPermissions)
    forDoc.setProperty(Classes.Permission.Fields.Permissions, newPermissions)
    forDoc.save()
    ()
  }

  private[this] def removeAllPermissionsForGranteeAndTarget(db: ODatabaseDocument,
                                                            grantee: PermissionGrantee,
                                                            forRecord: Option[ORID]): Try[Unit] = {
    for {
      permissionRids <- getAllPermissionsAndMap(db, grantee, forRecord, d => d.getIdentity)
      _ <- removePermissionsByRid(db, permissionRids, forRecord)
    } yield ()
  }

  private[this] def removePermissionsByRid(db: ODatabaseDocument,
                                           permissionRids: Set[ORID],
                                           forRecord: Option[ORID]): Try[Unit] = {
    for {
      _ <- forRecord match {
        case Some(forRid) =>
          Try {
            val forDoc = forRid.getRecord[ODocument]
            val permissions: util.Set[ORID] = forDoc.field(Classes.Permission.Fields.Permissions)
            val newPermissions = new util.HashSet(permissions)
            newPermissions.removeAll(permissions)
            forDoc.field(Classes.Permission.Fields.Permissions, newPermissions)
            forDoc.save()
          }
        case None => Success(())
      }
      _ <- Try(permissionRids foreach db.delete)
    } yield ()
  }

  /**
   * This method removes permissions from both the permissions class, as well
   * as from the permissions field of the record the permissions are assigned
   * too, if it exists.
   *
   * @param permissions The permissions to remove.
   * @param assignedTo  The assignee to remove the permissions from, or None
   *                    to remove the permission from the world.
   * @param forRecord   The target to remove the permission from, or None
   *                    for tall targets.
   * @return A try indicating success or failure.
   */
  private[this] def removePermissions(permissions: Set[String],
                                      assignedTo: Option[ORID],
                                      forRecord: Option[ORID]): Try[Unit] = withDb { db =>
    for {
      permissionRids <- Try(permissions.map(getPermissionRid(db, _, assignedTo, forRecord).get))
      _ <- removePermissionsByRid(db, permissionRids, forRecord)
    } yield ()
  }

  private[this] def getPermissionRid(db: ODatabaseDocument,
                                     permission: String,
                                     assignedTo: Option[ORID],
                                     forRecord: Option[ORID]): Try[ORID] = {
    val assignedToRID = assignedTo.orNull
    val forRecordRID = forRecord.orNull
    OrientDBUtil.getIdentityFromSingleValueIndex(
      db,
      Classes.Permission.Indices.AssignedTo_ForRecord_Permission,
      List(assignedToRID, forRecordRID, permission))
  }

  private[this] def getPermissionsByGranteeAndTargetRid[T](db: ODatabaseDocument,
                                                           grantee: PermissionGrantee,
                                                           forRecord: Option[ORID],
                                                           mapper: ODocument => T): Try[Set[T]] = {
    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE ")
    val paramsWithForRecord = addForRecordParam(sb, Map(), forRecord)
    val paramsWithGrantee = addGranteeParam(sb, paramsWithForRecord, grantee)
    OrientDBUtil
      .queryAndMap(db, sb.toString(), paramsWithGrantee)(mapper)
      .map(_.toSet)
  }

  private[this] def addForRecordParam(sb: StringBuilder, params: Map[String, Any], forRecord: Option[ORID]): Map[String, Any] = {
    forRecord match {
      case Some(rid) =>
        sb.append(Classes.Permission.Fields.ForRecord)
        sb.append(" = :")
        sb.append(Classes.Permission.Fields.ForRecord)
        params + (Classes.Permission.Fields.ForRecord -> rid)
      case None =>
        sb.append(" not(")
        sb.append(Classes.Permission.Fields.ForRecord)
        sb.append(" is DEFINED)")
        params
    }
  }

  private[this] def addGranteeParam(sb: StringBuilder, params: Map[String, Any], assignedTo: PermissionGrantee): Map[String, Any] = {
    assignedTo match {
      case GrantedToRid(rid) =>
        sb.append(" AND ")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" = :")
        sb.append(Classes.Permission.Fields.AssignedTo)
        params + (Classes.Permission.Fields.AssignedTo -> rid)
      case GrantedToAnyUser =>
        sb.append(" AND ")
        sb.append("(")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" is DEFINED AND ")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(".@class = 'User')")
        params
      case GrantedToAnyGroup =>
        sb.append(" AND ")
        sb.append("(")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" is DEFINED AND ")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(".@class = 'UserGroup')")
        params
      case GrantedToWorld =>
        sb.append(" AND ")
        sb.append("not(")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" is DEFINED)")
        params
      case AnyGrantee =>
        params
    }
  }
}

object PermissionsStore {

  import schema.DomainSchema._

  /**
   * Represents a permission granted to a user.
   *
   * @param user       The user the permission is granted too.
   * @param permission The permission granted to the user.
   */
  final case class UserPermission(user: DomainUser, permission: String)

  /**
   * Represents a permission granted to a group.
   *
   * @param group      The group the permission is granted too.
   * @param permission The permission granted to the user.
   */
  final case class GroupPermission(group: UserGroup, permission: String)

  /**
   * Represents a permission granted to the world.
   *
   * @param permission The permission granted to the user.
   */
  final case class WorldPermission(permission: String)

  /**
   * Defines the target that a permission applies to.
   */
  sealed trait PermissionTarget

  /**
   * Specifies a global permission that is not scoped to a particular entity
   * in the system.
   */
  final case object GlobalPermissionTarget extends PermissionTarget

  /**
   * A super trait for any permission target that is not global.
   */
  sealed trait NonGlobalPermissionTarget extends PermissionTarget

  /**
   * Specifies a Chat as the target of a permission.
   *
   * @param chatId The id of the chat that is the target of the permission
   */
  final case class ChatPermissionTarget(chatId: String) extends NonGlobalPermissionTarget

  /////////////////////////////////////////////////////////////////////////////
  // Private API
  /////////////////////////////////////////////////////////////////////////////

  private def docToWorldPermission(doc: ODocument): WorldPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    WorldPermission(permission)
  }

  private def docToGroupPermission(doc: ODocument): GroupPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    val assignedTo: ODocument = doc.field(Classes.Permission.Fields.AssignedTo)
    val group: UserGroup = UserGroupStore.docToGroup(assignedTo)
    GroupPermission(group, permission)
  }

  private def docToUserPermission(doc: ODocument): UserPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    val assignedTo: ODocument = doc.field(Classes.Permission.Fields.AssignedTo)
    val user: DomainUser = DomainUserStore.docToDomainUser(assignedTo)
    UserPermission(user, permission)
  }

  private def resolveTarget(db: ODatabaseDocument, target: PermissionTarget): Try[Option[ORID]] = {
    target match {
      case GlobalPermissionTarget =>
        Success(None)
      case t: NonGlobalPermissionTarget =>
        resolveNonGlobalTarget(t, db).map(Some(_))
    }
  }

  private def resolveNonGlobalTarget(target: NonGlobalPermissionTarget, db: ODatabaseDocument): Try[ORID] = {
    target match {
      case ChatPermissionTarget(chatId) =>
        ChatStore.getChatRid(chatId, db)
    }
  }

  /**
   * The [[PermissionGrantee]] trait is an ADT that defines the who / what a
   * permission has been granted to. It is used internally to generate
   * queries that match certain permission grants.
   */
  private sealed trait PermissionGrantee

  private final case object AnyGrantee extends PermissionGrantee

  private final case object GrantedToWorld extends PermissionGrantee

  private final case object GrantedToAnyUser extends PermissionGrantee

  private final case object GrantedToAnyGroup extends PermissionGrantee

  private final case class GrantedToRid(rid: ORID) extends PermissionGrantee

}
