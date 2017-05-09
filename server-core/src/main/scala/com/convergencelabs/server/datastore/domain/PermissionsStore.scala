package com.convergencelabs.server.datastore.domain

import scala.util.Try

import scala.collection.JavaConverters.mapAsJavaMapConverter
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.PermissionsStore._
import com.convergencelabs.server.domain.DomainUser
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.{ Set => JavaSet }

import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.sql.OCommandSQL

sealed trait Permission {
  val permission: String
}

case class UserPermission(user: DomainUser, permission: String) extends Permission
case class GroupPermission(group: UserGroup, permission: String) extends Permission
case class WorldPermission(permission: String) extends Permission

object PermissionsStore {
  val PermissionClass = "Permission"

  object Fields {
    val AssignedTo = "assignedTo"
    val ForRecord = "forRecord"
    val Permission = "permission"

    val Permissions = "permissions"
  }

  def docToPermission(doc: ODocument): Permission = {

    if (doc.containsField("assignedTo")) {
      val assignedTo: ODocument = doc.field(Fields.AssignedTo)
      assignedTo.getClassName match {
        case DomainUserStore.ClassName =>
          docToUserPermission(doc)
        case UserGroupStore.ClassName =>
          docToGroupPermission(doc)
        case default =>
          throw new IllegalStateException("Unsupported Permissions Assignment")
      }
    } else {
      val permission: String = doc.field(Fields.Permission)
      WorldPermission(permission)
    }
  }

  def docToWorldPermission(doc: ODocument): WorldPermission = {
    val permission: String = doc.field(Fields.Permission)

    WorldPermission(permission)
  }

  def docToGroupPermission(doc: ODocument): GroupPermission = {
    val permission: String = doc.field(Fields.Permission)
    val assignedTo: ODocument = doc.field(Fields.AssignedTo)
    val group: UserGroup = UserGroupStore.docToGroup(assignedTo)

    GroupPermission(group, permission)
  }

  def docToUserPermission(doc: ODocument): UserPermission = {
    val permission: String = doc.field(Fields.Permission)
    val assignedTo: ODocument = doc.field(Fields.AssignedTo)
    val user: DomainUser = DomainUserStore.docToDomainUser(assignedTo)

    UserPermission(user, permission)
  }
}

class PermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def hasPermission(username: String, forRecord: ORID, permission: String): Try[Boolean] = tryWithDb { db =>
    val userRID = DomainUserStore.getUserRid(username, db).get
    val queryString =
      """SELECT count(*) as count
        |  FROM Permission
        |  WHERE (forRecord = :forRecord OR not(forRecord is DEFINED)) AND
        |        permission = :permission AND
        |    (not(assignedTo is DEFINED) OR
        |     assignedTo = :user OR
        |     (assignedTo.@class instanceof 'UserGroup' AND assignedTo.members contains :user))""".stripMargin
    val params = Map("user" -> userRID, "forRecord" -> forRecord, "permission" -> permission)
    val result = QueryUtil.lookupMandatoryDocument(queryString, params, db).get
    val count: Long = result.field("count")
    count > 0
  }

  def hasPermissions(username: String, forRecord: ORID, permissions: Set[String]): Try[Boolean] = tryWithDb { db =>
    val userRID = DomainUserStore.getUserRid(username, db).get
    val queryString =
      """SELECT permission
        |  FROM Permission
        |  WHERE forRecord = :forRecord AND
        |    (not(assignedTo is DEFINED) OR
        |     assignedTo = :user OR
        |     (assignedTo.@class instanceof 'UserGroup' AND assignedTo.members contains :user))""".stripMargin
    val params = Map("user" -> userRID, "forRecord" -> forRecord)
    val results = QueryUtil.query(queryString, params, db)
    ???
  }

  def permissionExists(permission: String, assignedTo: Option[ORID], forRecord: Option[ORID]): Try[Boolean] = tryWithDb { db =>
    var params = Map[String, Any]("permission" -> permission)

    val sb = new StringBuilder
    sb.append("SELECT permission FROM Permission WHERE permission = :permission ")

    assignedTo.foreach { assignedTo =>
      sb.append("AND assignedTo = :assignedTo ")
      params += Fields.AssignedTo -> assignedTo
    }

    forRecord.foreach { forRecord =>
      sb.append("AND forRecord = :forRecord")
      params += Fields.ForRecord -> forRecord
    }

    QueryUtil.hasResults(sb.toString(), params, db)
  }

  def addWorldPermission(permission: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    var doc = new ODocument(PermissionClass)
    doc.field(Fields.Permission, permission)
    forRecord.foreach { doc.field(Fields.ForRecord, _) }
    doc = doc.save()

    forRecord.foreach { addPermissionToSet(_, doc.getIdentity) }
  }

  def addUserPermission(permission: String, username: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    val userRid = DomainUserStore.getUserRid(username, db).get

    var doc = new ODocument(PermissionClass)
    doc.field(Fields.Permission, permission)
    doc.field(Fields.AssignedTo, userRid)
    forRecord.foreach { doc.field(Fields.ForRecord, _) }
    doc = doc.save()

    forRecord.foreach { addPermissionToSet(_, doc.getIdentity) }
  }

  def addGroupPermission(permission: String, groupId: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    val groupRid = UserGroupStore.getGroupRid(groupId, db).get

    var doc = new ODocument(PermissionClass)
    doc.field(Fields.Permission, permission)
    doc.field(Fields.AssignedTo, groupRid)
    forRecord.foreach { doc.field(Fields.ForRecord, _) }
    doc = doc.save()

    forRecord.foreach { addPermissionToSet(_, doc.getIdentity) }
  }

  def addPermission(permission: String, assignedTo: Option[ORID], forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    var doc = new ODocument(PermissionClass)
    doc.field(Fields.Permission, permission)
    assignedTo.foreach { doc.field(Fields.AssignedTo, _) }
    forRecord.foreach { doc.field(Fields.ForRecord, _) }
    doc = doc.save()

    forRecord.foreach { addPermissionToSet(_, doc.getIdentity) }
  }

  def removeWorldPermission(permission: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    removePermission(permission, None, forRecord)
  }

  def removeUserPermission(permission: String, username: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    val userRid = DomainUserStore.getUserRid(username, db).get
    removePermission(permission, Some(userRid), forRecord)
  }

  def removeGroupPermission(permission: String, groupId: String, forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    val groupRid = UserGroupStore.getGroupRid(groupId, db).get
    removePermission(permission, Some(groupRid), forRecord)
  }

  def removePermission(permission: String, assignedTo: Option[ORID], forRecord: Option[ORID]): Try[Unit] = tryWithDb { db =>
    var params = Map[String, Any]("permission" -> permission)

    val sb = new StringBuilder
    sb.append("DELETE FROM Permission WHERE permission = :permission AND ")
    params = addOptionFieldParam(sb, params, Fields.AssignedTo, assignedTo)
    sb.append(" AND ")
    params = addOptionFieldParam(sb, params, Fields.ForRecord, forRecord)

    val command = new OCommandSQL(sb.toString())
    db.command(command).execute(params.asJava)
    ()
  }

  def getWorldPermissions(forRecord: Option[ORID]): Try[Set[WorldPermission]] = tryWithDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT permission FROM Permission WHERE not(assignedTo is DEFINED) AND ")
    params = addOptionFieldParam(sb, params, Fields.ForRecord, forRecord)

    val results = QueryUtil.query(sb.toString(), params, db)
    results.map { docToWorldPermission(_) }.toSet
  }

  def getGroupPermissions(forRecord: Option[ORID]): Try[Set[GroupPermission]] = tryWithDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE assignedTo is DEFINED AND assignedTo.@class instanceof 'UserGroup' AND ")
    params = addOptionFieldParam(sb, params, Fields.ForRecord, forRecord)

    val results = QueryUtil.query(sb.toString(), params, db)
    results.map { docToGroupPermission(_) }.toSet
  }

  def getUserPermissions(forRecord: Option[ORID]): Try[Set[UserPermission]] = tryWithDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE assignedTo is DEFINED AND assignedTo.@class instanceof 'User' AND ")
    params = addOptionFieldParam(sb, params, Fields.ForRecord, forRecord)

    val results = QueryUtil.query(sb.toString(), params, db)
    results.map { docToUserPermission(_) }.toSet
  }

  def getAllPermissions(forRecord: Option[ORID]): Try[Set[Permission]] = tryWithDb { db =>
    var params = Map[String, Any]()

    val sb = new StringBuilder
    sb.append("SELECT FROM Permission WHERE ")
    params = addOptionFieldParam(sb, params, Fields.ForRecord, forRecord)

    val results = QueryUtil.query(sb.toString(), params, db)
    results.map { docToPermission(_) }.toSet
  }

  private[this] def addPermissionToSet(forRecord: ORID, permission: ORID): Try[Unit] = tryWithDb { db =>
    val forDoc = forRecord.getRecord[ODocument]
    val permissions: JavaSet[ORID] = forDoc.field(Fields.Permissions)
    permissions.add(permission)
    forDoc.field(Fields.Permissions, permissions)
    forDoc.save()
    ()
  }

  private[this] def addOptionFieldParam(sb: StringBuilder, params: Map[String, Any], field: String, rid: Option[ORID]): Map[String, Any] = {
    var vParams = params
    rid match {
      case Some(rid) =>
        sb.append(s"$field = :$field")
        vParams += field -> rid
      case None =>
        sb.append(s"not($field is DEFINED)")
    }
    vParams
  }
}
