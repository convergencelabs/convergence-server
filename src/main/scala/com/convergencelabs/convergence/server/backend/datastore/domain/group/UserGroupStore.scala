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

package com.convergencelabs.convergence.server.backend.datastore.domain.group

import java.util

import com.convergencelabs.convergence.server.backend.datastore.domain.schema
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, EntityNotFoundException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.group.{UserGroup, UserGroupInfo, UserGroupSummary}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

class UserGroupStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import UserGroupStore._
  import schema.UserGroupClass._

  def createUserGroup(group: UserGroup): Try[Unit] = withDb { db =>
    groupToDoc(group, db)
      .flatMap { userDoc =>
        Try {
          db.save(userDoc)
          ()
        }
      } recoverWith handleDuplicateValue
  }

  def deleteUserGroup(id: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def updateUserGroupInfo(currentId: String, info: UserGroupInfo): Try[Unit] = withDb { db =>
    val UserGroupInfo(id, description) = info
    val params = Map("id" -> currentId)
    val query = "SELECT FROM UserGroup WHERE id = :id"
    OrientDBUtil
      .getDocument(db, query, params)
      .map { doc =>
        doc.setProperty(Fields.Id, id)
        doc.setProperty(Fields.Description, description)
        doc.save()
        ()
      } recoverWith handleDuplicateValue
  }

  def updateUserGroup(groupId: String, update: UserGroup): Try[Unit] = withDb { db =>
    groupToDoc(update, db).flatMap { updatedDoc =>
      OrientDBUtil
        .getDocumentFromSingleValueIndex(db, Indices.Id, groupId)
        .map { doc =>
          doc.merge(updatedDoc, false, false)
          doc.save()
          ()
        }
    } recoverWith handleDuplicateValue
  }

  def setGroupMembers(groupId: String, members: Set[DomainUserId]): Try[Unit] = withDb { db =>
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, Indices.Id, groupId)
      .flatMap { doc =>
        Try {
          val memberRids = members.map(m => DomainUserStore.getUserRid(m, db).get)
          doc.field("members", memberRids.asJava)
          doc.save()
          ()
        }
      }
  }

  def addUserToGroup(groupId: String, userId: DomainUserId): Try[Unit] = withDb { db =>
    DomainUserStore.getUserRid(userId, db)
      .recoverWith {
        case _: EntityNotFoundException =>
          Failure(EntityNotFoundException(s"Could not add user to group, because the user does not exists: $userId"))
      }.flatMap { userRid =>
      val command = "UPDATE UserGroup SET members = members || :user WHERE id = :id"
      val params = Map("user" -> userRid, "id" -> groupId)
      OrientDBUtil.mutateOneDocument(db, command, params)
    }.recoverWith {
      case _: EntityNotFoundException =>
        Failure(EntityNotFoundException(s"Could not add user to group, because the group does not exists: $groupId"))
    }
  }

  def setGroupsForUser(userId: DomainUserId, groups: Set[String]): Try[Unit] = withDb { db =>
    // TODO this approach will ignore setting a group that doesn't exist. Is this ok?
    DomainUserStore.getUserRid(userId, db)
      .recoverWith {
        case _: EntityNotFoundException =>
          Failure(EntityNotFoundException(s"Could not remove user from group, because the user does not exists: $userId"))
      }.flatMap { userRid =>
      val params = Map("user" -> userRid, "groups" -> groups.asJava)
      val command = "UPDATE UserGroup REMOVE members = :user WHERE :user IN members AND id NOT IN :groups"
      OrientDBUtil.commandReturningCount(db, command, params).flatMap { _ =>
        val addCommand = "UPDATE UserGroup SET members = members || :user WHERE id IN :groups"
        OrientDBUtil.commandReturningCount(db, addCommand, params).map(_ => ())
      }
    }
  }

  def removeUserFromGroup(id: String, userId: DomainUserId): Try[Unit] = withDb { db =>
    DomainUserStore.getUserRid(userId, db)
      .recoverWith {
        case cause: EntityNotFoundException =>
          Failure(EntityNotFoundException(
            s"Could not remove user from group, because the user does not exists: $userId",
            Some(userId)))
      }.flatMap { userRid =>
      val query = "UPDATE UserGroup REMOVE members = :user WHERE id = :id"
      val params = Map("user" -> userRid, "id" -> id)
      OrientDBUtil.mutateOneDocument(db, query, params)
    }.recoverWith {
      case cause: EntityNotFoundException =>
        Failure(EntityNotFoundException(
          s"Could not remove user from group, because the group does not exists: $id",
          Some(id)))
    }
  }

  def getUserGroup(id: String): Try[Option[UserGroup]] = withDb { db =>
    val query = "SELECT FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil.findDocumentAndMap(db, query, params)(docToGroup)
  }

  def getUserGroupsById(ids: List[String]): Try[List[UserGroup]] = withDb { db =>
    val query = "SELECT FROM UserGroup WHERE id IN :ids"
    val params = Map("ids" -> ids.asJava)
    OrientDBUtil
      .queryAndMap(db, query, params)(docToGroup)
      .flatMap { results =>
        Try {
          val mapped: Map[String, UserGroup] = results.map(a => a.id -> a).toMap
          val orderedList = ids.map(id => mapped.getOrElse(id, throw EntityNotFoundException(entityId = Some(id))))
          orderedList
        }
      }
  }

  def getUserGroupIdsForUsers(userIds: List[DomainUserId]): Try[Map[DomainUserId, Set[String]]] = tryWithDb { db =>
    val result: Map[DomainUserId, Set[String]] = userIds
      .map(userId => userId -> this.getUserGroupIdsForUser(userId).get).toMap
    result
  }

  def getUserGroupIdsForUser(userId: DomainUserId): Try[Set[String]] = withDb { db =>
    DomainUserStore.getUserRid(userId, db).flatMap { userRid =>
      val query = "SELECT id FROM UserGroup WHERE :user IN members"
      val params = Map("user" -> userRid)
      OrientDBUtil
        .queryAndMap(db, query, params)(_.field("id").asInstanceOf[String])
        .map(_.toSet)
    }.recoverWith {
      case cause: EntityNotFoundException =>
        Failure(EntityNotFoundException(entityId = Some(userId)))
    }
  }

  def getUserGroupInfo(id: String): Try[Option[UserGroupInfo]] = withDb { db =>
    val query = "SELECT id, description FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil.findDocumentAndMap(db, query, params) { doc =>
      UserGroupInfo(
        doc.field("id"),
        doc.field("description"))
    }
  }

  def getUserGroupSummary(id: String): Try[Option[UserGroupSummary]] = withDb { db =>
    val query = "SELECT id, description, members.size() as size FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil.findDocumentAndMap(db, query, params) { doc =>
      UserGroupSummary(
        doc.field("id"),
        doc.field("description"),
        doc.field("size"))
    }
  }

  def getUserGroupSummaries(filter: Option[String], offset: QueryOffset, limit: QueryLimit): Try[List[UserGroupSummary]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    val where = filter.map { f =>
      params("filter") = s"%${f}%"
      "WHERE id LIKE :filter || description LIKE :filter"
    } getOrElse ""

    val baseQuery = s"SELECT id, description, members.size() as size FROM UserGroup $where ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil
      .queryAndMap(db, query) { doc =>
        UserGroupSummary(
          doc.field("id"),
          doc.field("description"),
          doc.field("size"))
      }
  }

  def getUserGroups(filter: Option[String], offset: QueryOffset, limit: QueryLimit): Try[List[UserGroup]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    val where = filter.map { f =>
      params("filter") = s"%${f}%"
      "WHERE id LIKE :filter || description LIKE :filter"
    } getOrElse ""

    val baseQuery = s"SELECT FROM UserGroup $where ORDER BY id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    OrientDBUtil.queryAndMap(db, query, params.toMap)(docToGroup)
  }

  def userGroupExists(id: String): Try[Boolean] = withDb { db =>
    val query = "SELECT id FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil
      .query(db, query, params)
      .map {
        case _ :: Nil => true
        case _ => false
      }
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Id =>
          Failure(DuplicateValueException(Fields.Id))
        case _ =>
          Failure(e)
      }
  }
}


object UserGroupStore {

  import schema.UserGroupClass._

  def getGroupRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    // FIXME use index
    val query = "SELECT @RID as rid FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    OrientDBUtil
      .getDocument(db, query, params)
      .map(_.eval("rid").asInstanceOf[ORID])
  }

  def groupToDoc(group: UserGroup, db: ODatabaseDocument): Try[ODocument] = {
    Try {
      val UserGroup(id, description, members) = group
      val doc: ODocument = db.newInstance(ClassName)
      doc.setProperty(Fields.Id, id)
      doc.setProperty(Fields.Description, description)

      val memberRids = members.map { m =>
        DomainUserStore.getUserRid(m, db).get
      }

      doc.setProperty(Fields.Members, memberRids.asJava)

      doc
    }
  }

  def docToGroup(doc: ODocument): UserGroup = {
    val members: util.Set[ODocument] = doc.getProperty(Fields.Members)
    val membersScala = members.asScala.toSet

    UserGroup(
      doc.field(Fields.Id),
      doc.field(Fields.Description),
      membersScala.map { m =>
        DomainUserId(
          DomainUserType.withName(m.getProperty(DomainSchema.Classes.User.Fields.UserType)),
          m.getProperty(DomainSchema.Classes.User.Fields.Username).asInstanceOf[String])
      })
  }
}