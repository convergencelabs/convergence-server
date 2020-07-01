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

package com.convergencelabs.convergence.server.backend.datastore.domain.permissions

import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.permissions.SetChatPermissionsProcessor.{toTry, unsafeToTry}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
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

  import PermissionsStore._
  import schema.DomainSchema._

  /////////////////////////////////////////////////////////////////////////////
  // Permissions Checks
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Determines if a user has a specific permission for a target.
   *
   * @param userId     The userId of the user to check.
   * @param target     The target to check the permission for.
   * @param permission The permission to see if the user has.
   * @return True if the user has the specified permission, false otherwise.
   */
  def userHasPermission(userId: DomainUserId, target: PermissionTarget, permission: String): Try[Boolean] = withDb { db =>
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
      forRecord <- resolveTarget(db, target)
      (forClause, params) = forRecord match {
        case Some(rid) =>
          (
            "(not(forRecord IS DEFINED) OR forRecord = :forRecord) AND",
            Map("user" -> userRid, "forRecord" -> rid, "permission" -> permission)
          )
        case None =>
          (
            "not(forRecord is DEFINED) AND",
            Map("user" -> userRid, "permission" -> permission)
          )
      }
      query =
      s"""SELECT count(*) as count
         |  FROM Permission
         |  WHERE
         |    permission = :permission AND
         |    $forClause
         |    (
         |      not(assignedTo IS DEFINED) OR
         |      assignedTo = :user OR
         |      (assignedTo.@class = 'UserGroup' AND assignedTo.members CONTAINS :user)
         |    )""".stripMargin
      has <- OrientDBUtil
        .getDocument(db, query, params)
        .map(doc => doc.getProperty("count").asInstanceOf[Long] > 0)
    } yield has
  }

  /**
   * Gets the total sum of permissions a user has for a given target. This
   * combines user permissions, group permissions, world permissions for
   * the specific target as well as Global permissions the user might have.
   *
   * @param userId The id of the user to check permissions for.
   * @param target The target to check permissions for.
   * @param filter A set of permissions to include in the look up.
   * @return A set of permissions the user has for the given target and filter.
   */
  def getAggregateUserPermissionsForTarget(userId: DomainUserId,
                                           target: NonGlobalPermissionTarget,
                                           filter: Set[String]): Try[Set[String]] = withDb { db =>
    for {
      forRecord <- resolveNonGlobalTarget(db, target)
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
  // All Permissions
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Potentially sets all user, group, and world permissions for a specific target.
   * Each type of permission takes an optional set of permissions. If set to None
   * then no modifications to that type of permission will be made. For each type of
   * permission that is set to Some set of permissions, all existing permissions will
   * be removed and replaced with the specified set.
   *
   * @param target The target to re
   * @param user   A set of user permissions to set, or None to not modify user
   *               permissions.
   * @param group  A set of group permissions to set, or None to not modify group
   *               permissions.
   * @param world  A set of world permissions to set, or None to not modify world
   *               permissions.
   * @return Success(()) if the operation was successful, a Failure otherwise.
   */
  def setPermissionsForTarget(target: PermissionTarget,
                              user: Option[Set[UserPermissions]],
                              group: Option[Set[GroupPermissions]],
                              world: Option[Set[String]]): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- unsafeToTry(user) {
        _.foreach { case UserPermissions(userId, permissions) =>
          setPermissionsForUser(db, permissions, userId, forRecord).get
        }
      }
      _ <- unsafeToTry(group) {
        _.foreach { case GroupPermissions(group, permissions) =>
          setPermissionsForGroup(db, permissions, group, forRecord).get
        }
      }
      _ <- toTry(world) {
        setPermissionsForWorld(db, _, forRecord)
      }
    } yield {
      ()
    }
  }

  /**
   * Adds user, group, and world permissions to a specified target.
   *
   * @param target The target to add permissions to.
   * @param user   A set of user permissions to add.
   * @param group  A set of group permissions to add.
   * @param world  A set of world permissions to add.
   * @return Success(()) if the operation was successful, a Failure otherwise.
   */
  def addPermissionsForTarget(target: PermissionTarget,
                              user: Set[UserPermissions],
                              group: Set[GroupPermissions],
                              world: Set[String]): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- Try {
        user.foreach { case UserPermissions(userId, permissions) =>
          addPermissionsForUser(db, permissions, userId, forRecord).get
        }
      }
      _ <- Try {
        group.foreach { case GroupPermissions(group, permissions) =>
          addPermissionsForGroup(db, permissions, group, forRecord).get
        }
      }
      _ <- addPermissionsForWorld(db, world, forRecord)
    } yield {
      ()
    }
  }

  /**
   * Removes user, group, and world permissions to a specified target.
   *
   * @param target The target to add permissions to.
   * @param user   A set of user permissions to remove.
   * @param group  A set of group permissions to remove.
   * @param world  A set of world permissions to remove.
   * @return Success(()) if the operation was successful, a Failure otherwise.
   */
  def removePermissionsForTarget(target: PermissionTarget,
                                 user: Set[UserPermissions],
                                 group: Set[GroupPermissions],
                                 world: Set[String]): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- Try {
        user.foreach { case UserPermissions(userId, permissions) =>
          removePermissionsForUser(db, permissions, userId, forRecord).get
        }
      }
      _ <- Try {
        group.foreach { case GroupPermissions(group, permissions) =>
          removePermissionsForGroup(db, permissions, group, forRecord).get
        }
      }
      _ <- removePermissions(db, world, None, forRecord)
    } yield {
      ()
    }
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
      _ <- removePermissions(db, permissions, None, forRecord)
    } yield ()
  }

  def setPermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- setPermissionsForWorld(db, permissions, forRecord)
    } yield ()
  }

  private[this] def setPermissionsForWorld(db: ODatabaseDocument, permissions: Set[String], forRecord: Option[ORID]): Try[Unit] = {
    for {
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToWorld, forRecord)
      _ <- addPermissionsForWorld(db, permissions, forRecord)
    } yield ()
  }

  private[this] def addPermissionsForWorld(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           forRecord: Option[ORID]): Try[Unit] = {
    (for {
      permissionRids <- createPermissions(db, permissions, None, forRecord)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, forRecord))
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
      forRecord <- resolveTarget(db, target)
      _ <- setPermissionsForUser(db, permissions, userId, forRecord)
    } yield ()
  }

  def removePermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- removePermissionsForUser(db, permissions, userId, forRecord)
    } yield ()
  }

  def removeAllPermissionsForUser(userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), forRecord)
    } yield ()
  }

  private[this] def removePermissionsForUser(db: ODatabaseDocument,
                                             permissions: Set[String],
                                             userId: DomainUserId,
                                             forRecord: Option[ORID]): Try[Unit] = {
    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removePermissions(db, permissions, Some(userRid), forRecord)
    } yield ()
  }

  private[this] def setPermissionsForUser(db: ODatabaseDocument,
                                          permissions: Set[String],
                                          userId: DomainUserId,
                                          forRecord: Option[ORID]): Try[Unit] = {
    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), forRecord)
      _ <- addPermissionsForUser(db, permissions, userId, forRecord)
    } yield ()
  }

  private[this] def addPermissionsForUser(db: ODatabaseDocument,
                                          permissions: Set[String],
                                          userId: DomainUserId,
                                          forRecord: Option[ORID]): Try[Unit] = {
    (for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      permissionRids <- createPermissions(db, permissions, Some(userRid), forRecord)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, forRecord))
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
      _ <- setPermissionsForGroup(db, permissions, groupId, forRecord)
    } yield ()
  }

  def removePermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      _ <- removePermissionsForGroup(db, permissions, groupId, forRecord)
    } yield ()
  }

  def removeAllPermissionsForGroup(groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      forRecord <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), forRecord)
    } yield ()
  }

  private[this] def removePermissionsForGroup(db: ODatabaseDocument,
                                              permissions: Set[String],
                                              groupId: String,
                                              forRecord: Option[ORID]): Try[Unit] = {
    for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removePermissions(db, permissions, Some(groupRid), forRecord)
    } yield ()
  }

  private[this] def setPermissionsForGroup(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           groupId: String,
                                           forRecord: Option[ORID]): Try[Unit] = {
    for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), forRecord)
      _ <- addPermissionsForGroup(db, permissions, groupId, forRecord)
    } yield ()
  }

  private[this] def addPermissionsForGroup(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           groupId: String,
                                           forRecord: Option[ORID]): Try[Unit] = {
    (for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      permissionRids <- createPermissions(db, permissions, Some(groupRid), forRecord)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, forRecord))
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

  private[this] def createPermissions(db: ODatabaseDocument,
                                      permissions: Set[String],
                                      assignedTo: Option[ORID],
                                      forRecord: Option[ORID]): Try[Set[ORID]] = Try {
    permissions.map { permission =>
      val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
      doc.setProperty(Classes.Permission.Fields.Permission, permission)
      assignedTo.foreach(doc.setProperty(Classes.Permission.Fields.AssignedTo, _))
      forRecord.foreach(doc.setProperty(Classes.Permission.Fields.ForRecord, _))
      db.save(doc)
      doc.getIdentity
    }
  }

  private[this] def addPermissionToTarget(db: ODatabaseDocument, permissions: Set[ORID], forRecord: Option[ORID]): Try[Unit] = {
    forRecord match {
      case Some(fr) =>
        addPermissionsToSet(db, fr, permissions)
      case None =>
        Success(())
    }
  }

  private[this] def docToPermissionString(doc: ODocument): String =
    doc.field(Classes.Permission.Fields.Permission).asInstanceOf[String]


  private[this] def getAllPermissionsAndMap[T](db: ODatabaseDocument,
                                               assignedTo: PermissionGrantee,
                                               forRecord: Option[ORID],
                                               mapper: ODocument => T): Try[Set[T]] = {
    getPermissionsByGranteeAndTargetRid(db, assignedTo, forRecord, mapper)
  }

  private[this] def addPermissionsToSet(db: ODatabaseDocument, forRecord: ORID, permissions: Set[ORID]): Try[Unit] = {
    val command = s"UPDATE :target SET permissions = permissions || :permissions"
    OrientDBUtil.mutateOneDocument(db, command, Map("target" -> forRecord, "permissions" -> permissions.asJava)).map(_ => ())
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
          val command = s"UPDATE :target REMOVE permissions = :permissions"
          OrientDBUtil.mutateOneDocument(db, command, Map("target" -> forRid, "permissions" -> permissionRids.asJava))
        case None =>
          Success(())
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
  private[this] def removePermissions(db: ODatabaseDocument,
                                      permissions: Set[String],
                                      assignedTo: Option[ORID],
                                      forRecord: Option[ORID]): Try[Unit] = {
    for {
      permissionRids <- Try(permissions.map(getPermissionRid(db, _, assignedTo, forRecord).get))
      _ <- removePermissionsByRid(db, permissionRids, forRecord)
    } yield ()
  }

  private[this] def getPermissionRid(db: ODatabaseDocument,
                                     permission: String,
                                     assignedTo: Option[ORID],
                                     forRecord: Option[ORID]): Try[ORID] = {
    val assignedToRid = assignedTo.orNull
    val forRecordRid = forRecord.orNull
    OrientDBUtil.getIdentityFromSingleValueIndex(
      db,
      Classes.Permission.Indices.AssignedTo_ForRecord_Permission,
      List(assignedToRid, forRecordRid, permission))
  }

  private[this] def getPermissionsByGranteeAndTargetRid[T](db: ODatabaseDocument,
                                                           grantee: PermissionGrantee,
                                                           forRecord: Option[ORID],
                                                           mapper: ODocument => T): Try[Set[T]] = {
    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE ")
    val paramsWithForRecord = addForRecordParam(sb, Map(), forRecord)
    val paramsWithGrantee = addGranteeParam(sb, paramsWithForRecord, grantee)
    val query = sb.toString()
    OrientDBUtil
      .queryAndMap(db, query, paramsWithGrantee)(mapper)
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
        sb.append("not(")
        sb.append(Classes.Permission.Fields.ForRecord)
        sb.append(" IS DEFINED)")
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
        sb.append(" IS DEFINED AND ")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(".@class = 'User')")
        params
      case GrantedToAnyGroup =>
        sb.append(" AND ")
        sb.append("(")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" IS DEFINED AND ")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(".@class = 'UserGroup')")
        params
      case GrantedToWorld =>
        sb.append(" AND ")
        sb.append("not(")
        sb.append(Classes.Permission.Fields.AssignedTo)
        sb.append(" IS DEFINED)")
        params
      case AnyGrantee =>
        params
    }
  }
}

object PermissionsStore {

  import schema.DomainSchema._

  private def docToWorldPermission(doc: ODocument): WorldPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    WorldPermission(permission)
  }

  private val GroupIdExpression = s"${Classes.Permission.Fields.AssignedTo}.${Classes.UserGroup.Fields.Id}"

  private def docToGroupPermission(doc: ODocument): GroupPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)

    val groupId = doc.eval(GroupIdExpression).asInstanceOf[String]
    GroupPermission(groupId, permission)
  }

  private val UsernameExpression = s"${Classes.Permission.Fields.AssignedTo}.${Classes.User.Fields.Username}"
  private val UserTypeExpression = s"${Classes.Permission.Fields.AssignedTo}.${Classes.User.Fields.UserType}"

  private def docToUserPermission(doc: ODocument): UserPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)
    val username = doc.eval(UsernameExpression).asInstanceOf[String]
    val userType = doc.eval(UserTypeExpression).asInstanceOf[String]
    val userId = DomainUserId(userType, username)
    UserPermission(userId, permission)
  }

  private def resolveTarget(db: ODatabaseDocument, target: PermissionTarget): Try[Option[ORID]] = {
    target match {
      case GlobalPermissionTarget =>
        Success(None)
      case t: NonGlobalPermissionTarget =>
        resolveNonGlobalTarget(db, t).map(Some(_))
    }
  }

  private def resolveNonGlobalTarget(db: ODatabaseDocument, target: NonGlobalPermissionTarget): Try[ORID] = {
    target match {
      case ChatPermissionTarget(chatId) =>
        ChatStore.getChatRid(chatId, db)
    }
  }
}
