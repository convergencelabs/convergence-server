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

import com.convergencelabs.convergence.server.backend.datastore.domain.activity.ActivityStore
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.services.domain.permissions.AllPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

/**
 * The [PermissionStore] provides persistence for the permission subsystem.
 * Permission are scoped to Grantees which define who the permission is
 * granted to, as well as targets which define what the permission applies
 * to. Grantees can be the World (everyone), a User, or a User Group.
 * Targets can be globally scoped, or scoped to one of the entities in
 * the system such as a Chat or Activity.
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
    //   2. We must match permissions with this specific target and permissions
    //      that don't have a target defined, since those are global permissions
    //      that apply to all records that permission applies to.
    //   3. We much permissions that don't have an grantee field since those are
    //      world permissions. If there is an grantee value then the assigned to
    //      value can be this users, or a group this user belongs to.

    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      target <- resolveTarget(db, target)
      (forClause, params) = target match {
        case Some(rid) =>
          (
            "(not(target IS DEFINED) OR target = :target) AND",
            Map("user" -> userRid, "target" -> rid, "permission" -> permission)
          )
        case None =>
          (
            "not(target is DEFINED) AND",
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
           |      not(grantee IS DEFINED) OR
           |      grantee = :user OR
           |      (grantee.@class = 'UserGroup' AND grantee.members CONTAINS :user)
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
  def resolveUserPermissionsForTarget(userId: DomainUserId,
                                      target: NonGlobalPermissionTarget,
                                      filter: Set[String]): Try[Set[String]] = withDb { db =>
    for {
      target <- resolveNonGlobalTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      query =
        """SELECT permission
          |  FROM Permission
          |  WHERE
          |    permission in :permissions AND
          |    (not(target IS DEFINED) OR target = :target) AND
          |    (
          |      not(grantee IS DEFINED) OR
          |      grantee = :user OR
          |      (grantee.@class = 'UserGroup' AND grantee.members CONTAINS :user)
          |    )""".stripMargin
      params = Map("user" -> userRid, "target" -> target, "permissions" -> filter.asJava)
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
                              user: Option[Map[DomainUserId, Set[String]]],
                              replaceUsers: Boolean,
                              group: Option[Map[String, Set[String]]],
                              replaceGroups: Boolean,
                              world: Option[Set[String]],
                             ): Try[Unit] = withDbTransaction { db =>
    for {
      targetRid <- resolveTarget(db, target)
      _ <- user.map(setUserPermissions(targetRid, _, replaceUsers, db)).getOrElse(Success(()))
      _ <- group.map(setGroupPermissions(targetRid, _, replaceGroups, db)).getOrElse(Success(()))
      _ <- world.map(setPermissionsForWorld(db, _, targetRid)).getOrElse(Success(()))
    } yield ()
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
                              user: Map[DomainUserId, Set[String]],
                              group: Map[String, Set[String]],
                              world: Set[String]): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- Try {
        user.foreach { case (userId, permissions) =>
          addPermissionsForUser(db, permissions, userId, target).get
        }
      }
      _ <- Try {
        group.foreach { case (group, permissions) =>
          addPermissionsForGroup(db, permissions, group, target).get
        }
      }
      _ <- addPermissionsForWorld(db, world, target)
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
                                 user: Map[DomainUserId, Set[String]],
                                 group: Map[String, Set[String]],
                                 world: Set[String]): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- Try {
        user.foreach { case (userId, permissions) =>
          removePermissionsForUser(db, permissions, userId, target).get
        }
      }
      _ <- Try {
        group.foreach { case (group, permissions) =>
          removePermissionsForGroup(db, permissions, group, target).get
        }
      }
      _ <- removePermissions(db, world, None, target)
    } yield ()
  }

  /**
   * Gets all permissions for a given target.
   *
   * @param target The target to get permissions for.
   * @return Success(AllPermissions) if the operation was successful, a Failure otherwise.
   */
  def getPermissionsForTarget(target: PermissionTarget): Try[AllPermissions] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      permissionDocs <- getPermissionsByGranteeAndTargetRid(db, AnyGrantee, target, doc => doc)
    } yield {
      val grouped = permissionDocs.groupBy(doc =>
        Option(doc.eval("grantee.@class").asInstanceOf[String]).getOrElse("world"))

      val groupPermissions = grouped
        .getOrElse(DomainSchema.Classes.UserGroup.ClassName, Set())
        .map(docToGroupPermission)
        .groupBy(_.groupId)
        .map { case (groupId, permissions) =>
          groupId -> permissions.map(_.permission)
        }

      val userPermissions = grouped
        .getOrElse(DomainSchema.Classes.User.ClassName, Set())
        .map(docToUserPermission)
        .groupBy(_.userId)
        .map { case (userId, permissions) =>
          userId -> permissions.map(_.permission)
        }

      val worldPermissions = grouped
        .getOrElse("world", Set())
        .map(docToWorldPermission)


      AllPermissions(worldPermissions, userPermissions, groupPermissions)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // World Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getPermissionsForWorld(target: PermissionTarget): Try[Set[String]] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToWorld, target, docToWorldPermission)
    } yield permissions
  }

  def addPermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- addPermissionsForWorld(db, permissions, target)
    } yield ()
  }

  def removePermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- removePermissions(db, permissions, None, target)
    } yield ()
  }

  def setPermissionsForWorld(permissions: Set[String], target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- setPermissionsForWorld(db, permissions, target)
    } yield ()
  }

  private[this] def setPermissionsForWorld(db: ODatabaseDocument, permissions: Set[String], target: Option[ORID]): Try[Unit] = {
    for {
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToWorld, target)
      _ <- addPermissionsForWorld(db, permissions, target)
    } yield ()
  }

  private[this] def addPermissionsForWorld(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           target: Option[ORID]): Try[Unit] = {
    (for {
      permissionRids <- createMissingPermissions(db, permissions, None, target)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, target))
  }

  /////////////////////////////////////////////////////////////////////////////
  // User Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getUserPermissionsForTarget(target: PermissionTarget): Try[Set[UserPermission]] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToAnyUser, target, docToUserPermission)
    } yield permissions
  }

  def getPermissionsForUser(userId: DomainUserId, target: PermissionTarget): Try[Set[String]] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      permissions <- getAllPermissionsAndMap(db, GrantedToRid(userRid), target, docToPermissionString)
    } yield permissions
  }

  def addPermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- addPermissionsForUser(db, permissions, userId, target)
    } yield ()
  }

  def setPermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- setPermissionsForUser(db, permissions, userId, target)
    } yield ()
  }

  def removePermissionsForUser(permissions: Set[String], userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- removePermissionsForUser(db, permissions, userId, target)
    } yield ()
  }

  def removeAllPermissionsForUserByTarget(userId: DomainUserId, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), target)
    } yield ()
  }

  def removeAllPermissionsForUser(userId: DomainUserId): Try[Unit] = withDb { db =>
    val command =
      """
        |let users = SELECT FROM User WHERE userType = :userType AND username = :username;
        |let user = $users[0];
        |UPDATE PermissionTarget REMOVE permissions = permissions[grantee = $user] WHERE permissions CONTAINS(grantee = $user)
        |DELETE FROM permission WHERE grantee = $user
        |""".stripMargin
    val params = Map("userType" -> userId.userType, "username" -> userId.username)
    OrientDBUtil.execute(db, command, params).map(_ => ())
  }

  private[this] def removePermissionsForUser(db: ODatabaseDocument,
                                             permissions: Set[String],
                                             userId: DomainUserId,
                                             target: Option[ORID]): Try[Unit] = {
    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removePermissions(db, permissions, Some(userRid), target)
    } yield ()
  }

  private[this] def setPermissionsForUser(db: ODatabaseDocument,
                                          permissions: Set[String],
                                          userId: DomainUserId,
                                          target: Option[ORID]): Try[Unit] = {
    for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(userRid), target)
      _ <- addPermissionsForUser(db, permissions, userId, target)
    } yield ()
  }

  private[this] def addPermissionsForUser(db: ODatabaseDocument,
                                          permissions: Set[String],
                                          userId: DomainUserId,
                                          target: Option[ORID]): Try[Unit] = {
    (for {
      userRid <- DomainUserStore.getUserRid(userId, db)
      permissionRids <- createMissingPermissions(db, permissions, Some(userRid), target)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, target))
  }

  private[this] def setUserPermissions(target: Option[ORID],
                                       userPermissions: Map[DomainUserId, Set[String]],
                                       replace: Boolean,
                                       db: ODatabaseDocument): Try[Unit] = {
    for {
      _ <- if (replace) {
        removeAllPermissionsForGranteeAndTarget(db, GrantedToAnyUser, target)
      } else {
        Success(())
      }
      _ <- Try {
        userPermissions.foreach { case (userId, permissions) =>
          setPermissionsForUser(db, permissions, userId, target).get
        }
      }
    } yield ()
  }

  /////////////////////////////////////////////////////////////////////////////
  // Group Permissions
  /////////////////////////////////////////////////////////////////////////////

  def getGroupPermissionsForTarget(target: PermissionTarget): Try[Set[GroupPermission]] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      permissions <- getPermissionsByGranteeAndTargetRid(db, GrantedToAnyGroup, target, docToGroupPermission)
    } yield permissions
  }

  def getPermissionsForGroup(groupId: String, target: PermissionTarget): Try[Set[String]] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      permissions <- getAllPermissionsAndMap(db, GrantedToRid(groupRid), target, docToPermissionString)
    } yield permissions
  }

  def addPermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- addPermissionsForGroup(db, permissions, groupId, target)
    } yield ()
  }

  def setPermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- setPermissionsForGroup(db, permissions, groupId, target)
    } yield ()
  }

  def removePermissionsForGroup(permissions: Set[String], groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- removePermissionsForGroup(db, permissions, groupId, target)
    } yield ()
  }

  def removeAllPermissionsForGroup(groupId: String, target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), target)
    } yield ()
  }

  private[this] def removePermissionsForGroup(db: ODatabaseDocument,
                                              permissions: Set[String],
                                              groupId: String,
                                              target: Option[ORID]): Try[Unit] = {
    for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removePermissions(db, permissions, Some(groupRid), target)
    } yield ()
  }

  private[this] def setPermissionsForGroup(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           groupId: String,
                                           target: Option[ORID]): Try[Unit] = {
    for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      _ <- removeAllPermissionsForGranteeAndTarget(db, GrantedToRid(groupRid), target)
      _ <- addPermissionsForGroup(db, permissions, groupId, target)
    } yield ()
  }

  private[this] def addPermissionsForGroup(db: ODatabaseDocument,
                                           permissions: Set[String],
                                           groupId: String,
                                           target: Option[ORID]): Try[Unit] = {
    (for {
      groupRid <- UserGroupStore.getGroupRid(groupId, db)
      permissionRids <- createMissingPermissions(db, permissions, Some(groupRid), target)
    } yield permissionRids)
      .flatMap(p => addPermissionToTarget(db, p, target))
  }

  private[this] def setGroupPermissions(target: Option[ORID],
                                        groupPermissions: Map[String, Set[String]],
                                        replace: Boolean,
                                        db: ODatabaseDocument): Try[Unit] = {
    for {
      _ <- if (replace) {
        removeAllPermissionsForGranteeAndTarget(db, GrantedToAnyGroup, target)
      } else {
        Success(())
      }
      _ <- Try {
        groupPermissions.foreach { case (groupId, permissions) =>
          setPermissionsForGroup(db, permissions, groupId, target).get
        }
      }
    } yield ()
  }

  def removeAllPermissionsForGroup(groupId: String): Try[Unit] = withDb { db =>
    val command =
      """
        |let groups = SELECT FROM UserGroup WHERE groupId = :groupId;
        |let group = $groups[0];
        |UPDATE PermissionTarget REMOVE permissions = permissions[grantee = $group] WHERE permissions CONTAINS(grantee = $group)
        |DELETE FROM permission WHERE grantee = $group
        |""".stripMargin
    val params = Map("groupId" -> groupId)
    OrientDBUtil.execute(db, command, params).map(_ => ())
  }

  /////////////////////////////////////////////////////////////////////////////
  // Target Permissions
  /////////////////////////////////////////////////////////////////////////////

  def removeAllPermissionsForTarget(target: PermissionTarget): Try[Unit] = withDbTransaction { db =>
    for {
      target <- resolveTarget(db, target)
      _ <- removeAllPermissionsForGranteeAndTarget(db, AnyGrantee, target)
    } yield ()
  }

  /////////////////////////////////////////////////////////////////////////////
  // General Helpers
  /////////////////////////////////////////////////////////////////////////////


  private[this] def createMissingPermissions(db: ODatabaseDocument,
                                             permissions: Set[String],
                                             grantee: Option[ORID],
                                             target: Option[ORID]): Try[Set[ORID]] = Try {
    permissions.map { permission =>
      val doc: ODocument = db.newInstance(Classes.Permission.ClassName)
      doc.setProperty(Classes.Permission.Fields.Permission, permission)
      grantee.foreach(doc.setProperty(Classes.Permission.Fields.Grantee, _))
      target.foreach(doc.setProperty(Classes.Permission.Fields.Target, _))
      (Try {
        db.save(doc)
        Some(doc.getIdentity)
      } recover {
        case _: ORecordDuplicatedException =>
          None
      }).get

    }.filter(_.isDefined).map(_.get)
  }

  private[this] def addPermissionToTarget(db: ODatabaseDocument, permissions: Set[ORID], target: Option[ORID]): Try[Unit] = {
    target match {
      case Some(fr) =>
        addPermissionsToSet(db, fr, permissions)
      case None =>
        Success(())
    }
  }

  private[this] def docToPermissionString(doc: ODocument): String =
    doc.field(Classes.Permission.Fields.Permission).asInstanceOf[String]


  private[this] def getAllPermissionsAndMap[T](db: ODatabaseDocument,
                                               grantee: PermissionGrantee,
                                               target: Option[ORID],
                                               mapper: ODocument => T): Try[Set[T]] = {
    getPermissionsByGranteeAndTargetRid(db, grantee, target, mapper)
  }

  private[this] def addPermissionsToSet(db: ODatabaseDocument, target: ORID, permissions: Set[ORID]): Try[Unit] = {
    val command = s"UPDATE :target SET permissions = permissions || :permissions"
    OrientDBUtil.mutateOneDocument(db, command, Map("target" -> target, "permissions" -> permissions.asJava)).map(_ => ())
  }

  private[this] def removeAllPermissionsForGranteeAndTarget(db: ODatabaseDocument,
                                                            grantee: PermissionGrantee,
                                                            target: Option[ORID]): Try[Unit] = {
    for {
      permissionRids <- getAllPermissionsAndMap(db, grantee, target, d => d.getIdentity)
      _ <- removePermissionsByRid(db, permissionRids, target)
    } yield ()
  }

  /**
   * This method removes permissions from both the permissions class, as well
   * as from the permissions field of the record the permissions are assigned
   * too, if it exists.
   *
   * @param db             The database instance to use.
   * @param permissionRids The permissions instances to remove.
   * @param target         The target to remove the permission from, or None
   *                       for the global targets.
   * @return A try indicating success or failure.
   */
  private[this] def removePermissionsByRid(db: ODatabaseDocument,
                                           permissionRids: Set[ORID],
                                           target: Option[ORID]): Try[Unit] = {
    for {
      _ <- target match {
        case Some(targetRid) =>
          val command = s"UPDATE :target REMOVE permissions = :permissions"
          OrientDBUtil.mutateOneDocument(db, command, Map("target" -> targetRid, "permissions" -> permissionRids.asJava))
        case None =>
          Success(())
      }
      // TODO doing this in a query would be more efficient.
      _ <- Try(permissionRids foreach db.delete)
    } yield ()
  }

  /**
   * This method removes permissions from both the permissions class, as well
   * as from the permissions field of the record the permissions are assigned
   * too, if it exists.
   *
   * @param permissions The permissions to remove.
   * @param grantee     The assignee to remove the permissions from, or None
   *                    to remove the permission from the world.
   * @param target      The target to remove the permission from, or None
   *                    for the global target.
   * @return A try indicating success or failure.
   */
  private[this] def removePermissions(db: ODatabaseDocument,
                                      permissions: Set[String],
                                      grantee: Option[ORID],
                                      target: Option[ORID]): Try[Unit] = {
    for {
      permissionRids <- Try(permissions.flatMap(findPermissionRid(db, _, grantee, target).get))
      _ <- removePermissionsByRid(db, permissionRids, target)
    } yield ()
  }

  private[this] def findPermissionRid(db: ODatabaseDocument,
                                      permission: String,
                                      grantee: Option[ORID],
                                      target: Option[ORID]): Try[Option[ORID]] = {
    val granteeRid = grantee.orNull
    val targetRid = target.orNull
    OrientDBUtil.findIdentityFromSingleValueIndex(
      db,
      Classes.Permission.Indices.Grantee_Target_Permission,
      List(granteeRid, targetRid, permission))
  }

  private[this] def getPermissionsByGranteeAndTargetRid[T](db: ODatabaseDocument,
                                                           grantee: PermissionGrantee,
                                                           target: Option[ORID],
                                                           mapper: ODocument => T): Try[Set[T]] = {
    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE ")
    val paramsWithTarget = addTargetParam(sb, Map(), target)
    val paramsWithGrantee = addGranteeParam(sb, paramsWithTarget, grantee)
    val query = sb.toString()
    OrientDBUtil
      .queryAndMap(db, query, paramsWithGrantee)(mapper)
      .map(_.toSet)
  }

  private[this] def addTargetParam(sb: StringBuilder, params: Map[String, Any], target: Option[ORID]): Map[String, Any] = {
    target match {
      case Some(rid) =>
        sb.append(Classes.Permission.Fields.Target)
        sb.append(" = :")
        sb.append(Classes.Permission.Fields.Target)
        params + (Classes.Permission.Fields.Target -> rid)

      case None =>
        sb.append("not(")
        sb.append(Classes.Permission.Fields.Target)
        sb.append(" IS DEFINED)")
        params
    }
  }

  private[this] def addGranteeParam(sb: StringBuilder, params: Map[String, Any], grantee: PermissionGrantee): Map[String, Any] = {
    grantee match {
      case GrantedToRid(rid) =>
        sb.append(" AND ")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" = :")
        sb.append(Params.Grantee)
        params + (Params.Grantee -> rid)

      case GrantedToUser(userId) =>
        sb.append(" AND ")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" = (SELECT FROM User WHERE ")
        sb.append(Classes.User.Fields.UserType)
        sb.append(" = :")
        sb.append(Params.UserType)
        sb.append(" AND ")
        sb.append(Classes.User.Fields.Username)
        sb.append(" = :")
        sb.append(Params.Username)
        sb.append(")")
        params ++ Map(Params.Username -> userId.username, Params.UserType -> userId.userType)

      case GrantedToGroup(groupId) =>
        sb.append(" AND ")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" = (SELECT FROM UserGroup WHERE ")
        sb.append(Classes.UserGroup.Fields.Id)
        sb.append(" = :")
        sb.append(Params.Id)
        sb.append(")")
        params ++ Map(Params.Id -> groupId)

      case GrantedToAnyUser =>
        sb.append(" AND ")
        sb.append("(")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" IS DEFINED AND ")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(".@class = 'User')")
        params

      case GrantedToAnyGroup =>
        sb.append(" AND ")
        sb.append("(")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" IS DEFINED AND ")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(".@class = 'UserGroup')")
        params

      case GrantedToWorld =>
        sb.append(" AND ")
        sb.append("not(")
        sb.append(Classes.Permission.Fields.Grantee)
        sb.append(" IS DEFINED)")
        params

      case AnyGrantee =>
        params
    }
  }
}

object PermissionsStore {

  import schema.DomainSchema._

  private object Params {
    val Target = "target"
    val Grantee = "grantee"
    val UserType = "userType"
    val Username = "username"
    val Id = "id"
  }

  private def docToWorldPermission(doc: ODocument): String = {
    doc.field(Classes.Permission.Fields.Permission).asInstanceOf[String]
  }

  private val GroupIdExpression = s"${Classes.Permission.Fields.Grantee}.${Classes.UserGroup.Fields.Id}"

  private def docToGroupPermission(doc: ODocument): GroupPermission = {
    val permission: String = doc.field(Classes.Permission.Fields.Permission)

    val groupId = doc.eval(GroupIdExpression).asInstanceOf[String]
    GroupPermission(groupId, permission)
  }

  private val UsernameExpression = s"${Classes.Permission.Fields.Grantee}.${Classes.User.Fields.Username}"
  private val UserTypeExpression = s"${Classes.Permission.Fields.Grantee}.${Classes.User.Fields.UserType}"

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
      case ActivityPermissionTarget(id) =>
        ActivityStore.getActivityRid(id, db)
    }
  }
}
