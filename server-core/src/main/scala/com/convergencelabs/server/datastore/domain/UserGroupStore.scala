package com.convergencelabs.server.datastore.domain

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.util.Failure
import scala.util.Try

import java.util.{ Set => JavaSet }

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.QueryUtil
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.metadata.schema.OType

case class UserGroup(id: String, description: String, members: Set[String])
case class UserGroupInfo(id: String, description: String)
case class UserGroupSummary(id: String, description: String, memberCount: Int)

object UserGroupStore {

  val ClassName = "UserGroup"
  val GroupIdIndex = "UserGroup.id"

  object Fields {
    val Id = "id"
    val Description = "description"
  }

  def getGroupRid(id: String, db: ODatabaseDocumentTx): Try[ORID] = {
    val query = "SELECT @RID as rid FROM UserGroup WHERE id = :id"
    val params = Map("id" -> id)
    QueryUtil.lookupMandatoryDocument(query, params, db) map { _.eval("rid").asInstanceOf[ORID] }
  }

  def groupToDoc(group: UserGroup, db: ODatabaseDocumentTx): ODocument = {
    val UserGroup(id, description, members) = group
    val doc = new ODocument(ClassName)
    doc.field(Fields.Id, id)
    doc.field(Fields.Description, description)
    val memberRids = members.map { m =>
      DomainUserStore.getUserRid(m, db).get
    }
    doc.field("members", memberRids.asJava)
  }

  def docToGroup(doc: ODocument): UserGroup = {
    val members: JavaSet[ODocument] = doc.field("members", OType.LINKSET)
    val membersScala = members.toSet

    UserGroup(
      doc.field(Fields.Id),
      doc.field(Fields.Description),
      membersScala.map(m => m.field("username").asInstanceOf[String]))
  }
}

class UserGroupStore private[domain] (private[this] val dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import UserGroupStore._

  def createUserGroup(group: UserGroup): Try[Unit] = tryWithDb { db =>
    val userDoc = groupToDoc(group, db)
    db.save(userDoc)
    ()
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def deleteUserGroup(id: String): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM UserGroup WHERE id = :id")
    val params = Map("id" -> id)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 =>
        throw EntityNotFoundException()
      case _ =>
        ()
    }
  }

  def updateUserGroupInfo(currentId: String, info: UserGroupInfo): Try[Unit] = tryWithDb { db =>
    val UserGroupInfo(id, description) = info
    val params = Map("id" -> currentId)
    QueryUtil.lookupMandatoryDocument("SELECT FROM UserGroup WHERE id = :id", params, db).map { doc =>
      doc.field(Fields.Id, id)
      doc.field(Fields.Description, description)
      doc.save()
      ()
    }.get
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def updateUserGroup(currentId: String, update: UserGroup): Try[Unit] = tryWithDb { db =>
    val updatedDoc = groupToDoc(update, db)
    val params = Map("id" -> currentId)
    QueryUtil.lookupMandatoryDocument("SELECT FROM UserGroup WHERE id = :id", params, db).map { doc =>
      doc.merge(updatedDoc, false, false)
      doc.save()
      ()
    }.get
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue(e)
  }

  def setGroupMembers(currentId: String, members: Set[String]): Try[Unit] = tryWithDb { db =>
    val params = Map("id" -> currentId)
    QueryUtil.lookupMandatoryDocument("SELECT FROM UserGroup WHERE id = :id", params, db).map { doc =>
      val memberRids = members.map { m =>
        DomainUserStore.getUserRid(m, db).get
      }
      doc.field("members", memberRids.asJava)
      doc.save()
      ()
    }.get
  }

  def addUserToGroup(id: String, username: String): Try[Unit] = tryWithDb { db =>
    DomainUserStore.getUserRid(username, db)
      .recoverWith {
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException(s"Could not add user to group, becase the user does not exists: ${username}"))
      }.flatMap { userRid =>
        val query = "UPDATE UserGroup ADD members = :user WHERE id = :id"
        val params = Map("user" -> userRid, "id" -> id)
        QueryUtil.updateSingleDoc(query, params, db)
      }.recoverWith {
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException(s"Could not add user to group, becase the group does not exists: ${id}"))
      }.get
  }

  def removeUserFromGroup(id: String, username: String): Try[Unit] = tryWithDb { db =>
    DomainUserStore.getUserRid(username, db)
      .recoverWith {
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException(s"Could not remove user from group, becase the user does not exists: ${username}"))
      }.flatMap { userRid =>
        val query = "UPDATE UserGroup REMOVE members = :user WHERE id = :id"
        val params = Map("user" -> userRid, "id" -> id)
        QueryUtil.updateSingleDoc(query, params, db)
      }.recoverWith {
        case cause: EntityNotFoundException =>
          Failure(new EntityNotFoundException(s"Could not remove user from group, becase the group does not exists: ${id}"))
      }.get
  }

  def getUserGroup(id: String): Try[Option[UserGroup]] = tryWithDb { db =>
    val params = Map("id" -> id)
    QueryUtil.lookupOptionalDocument("SELECT FROM UserGroup WHERE id = :id", params, db) map { docToGroup(_) }
  }

  def getUserGroupsById(ids: List[String]): Try[List[UserGroup]] = tryWithDb { db =>
    val params = Map("ids" -> ids.asJava)
    val results = QueryUtil.query("SELECT FROM UserGroup WHERE id IN :ids", params, db) map { docToGroup(_) }
    val mapped: Map[String, UserGroup] = results.map(a => a.id -> a)(collection.breakOut)
    val orderedList = ids.map(id => mapped.get(id).getOrElse(throw new EntityNotFoundException("id")))
    orderedList
  }

  def getUserGroupIdsForUsers(usernames: List[String]): Try[Map[String, List[String]]] = tryWithDb { db =>
    val result: Map[String, List[String]] = usernames
      .map(username => username -> this.getUserGroupIdsForUser(username).get)(collection.breakOut)
    result
  }

  def getUserGroupIdsForUser(username: String): Try[List[String]] = tryWithDb { db =>
    DomainUserStore.getUserRid(username, db).map { userRid =>
      val params = Map("user" -> userRid)
      QueryUtil.query("SELECT id FROM UserGroup WHERE :user IN members", params, db).map(_.field("id").asInstanceOf[String])
    }.get
  }

  def getUserGroupInfo(id: String): Try[Option[UserGroupInfo]] = tryWithDb { db =>
    val params = Map("id" -> id)
    QueryUtil.lookupOptionalDocument("SELECT id, description FROM UserGroup WHERE id = :id", params, db) map { doc =>
      UserGroupInfo(
        doc.field("id"),
        doc.field("description"))
    }
  }

  def getUserGroupSummary(id: String): Try[Option[UserGroupSummary]] = tryWithDb { db =>
    val params = Map("id" -> id)
    QueryUtil.lookupOptionalDocument("SELECT id, description, members.size() as size FROM UserGroup WHERE id = :id", params, db) map { doc =>
      UserGroupSummary(
        doc.field("id"),
        doc.field("description"),
        doc.field("size"))
    }
  }

  def getUserGroupSummaries(filter: Option[String], offset: Option[Int], limit: Option[Int]): Try[List[UserGroupSummary]] = tryWithDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    val where = filter.map { f =>
      params("filter") = s"%${f}%"
      "WHERE id LIKE :filter || description LIKE :filter"
    } getOrElse ("")

    val baseQuery = s"SELECT id, description, members.size() as size FROM UserGroup ${where} ORDER BY id ASC"
    val query = QueryUtil.buildPagedQuery(baseQuery, limit, offset)
    QueryUtil.query(query, params.toMap, db).map { doc =>
      UserGroupSummary(
        doc.field("id"),
        doc.field("description"),
        doc.field("size"))
    }
  }

  def getUserGroups(filter: Option[String], offset: Option[Int], limit: Option[Int]): Try[List[UserGroup]] = tryWithDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    val where = filter.map { f =>
      params("filter") = s"%${f}%"
      "WHERE id LIKE :filter || description LIKE :filter"
    } getOrElse ("")

    val baseQuery = s"SELECT FROM UserGroup ${where} ORDER BY id ASC"
    val query = QueryUtil.buildPagedQuery(baseQuery, limit, offset)
    QueryUtil.query(query, params.toMap, db).map { docToGroup(_) }
  }

  def userGroupExists(id: String): Try[Boolean] = tryWithDb { db =>
    QueryUtil.query("SELECT id FROM UserGroup WHERE id = :id", Map("id" -> id), db) match {
      case doc :: Nil => true
      case _ => false
    }
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case UserGroupStore.GroupIdIndex =>
        Failure(DuplicateValueException(UserGroupStore.Fields.Id))
      case _ =>
        Failure(e)
    }
  }
}
