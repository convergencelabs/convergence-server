package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.PermissionsStore._
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import scala.util.Try
import com.orientechnologies.orient.core.id.ORID
import grizzled.slf4j.Logging
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import com.orientechnologies.orient.core.index.OCompositeKey
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.{ List => JavaList }
import com.orientechnologies.orient.core.metadata.schema.OType
import java.util.HashSet
import com.orientechnologies.orient.core.sql.OCommandSQL

case class Permission(id: String, name: String, description: String)
case class Role(name: String, permissions: List[String], description: String)
case class UserRoles(username: String, roles: Set[String])

object PermissionsStore {
  val PermissionClassName = "Permission"
  val RoleClassName = "Role"
  val UserDomainRoleClassName = "UserDomainRole"

  val PermissionIndex = "Permission.id"
  val RoleIndex = "Role.id"

  val UsernameIndex = "User.username"
  val DomainNamespaceIdIndex = "Domain.namespace_id"

  object Fields {
    val ID = "id"
    val Name = "name"
    val Description = "description"

    val Permissions = "permissions"

    val User = "user"
    val Domain = "domain"
    val Role = "role"
  }

  def docToPermission(doc: ODocument): Permission = {
    Permission(
      doc.field(Fields.ID),
      doc.field(Fields.Name),
      doc.field(Fields.Description))
  }

  def docToRole(doc: ODocument): Role = {
    val permissionDocs: JavaList[ODocument] = doc.field(Fields.Permissions)
    val permissions = permissionDocs.asScala.map { permisionDoc =>
      val permission: String = permisionDoc.field(Fields.ID)
      permission
    }.toList

    Role(
      doc.field(Fields.Name),
      permissions,
      doc.field(Fields.Description))
  }
}

/**
 * Manages the persistence of Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new UserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
 */
class PermissionsStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def hasBeenSetup() = tryWithDb { db =>
    db.getMetadata.getIndexManager.getIndex(PermissionIndex).getSize > 0
  }

  def createPermission(permission: Permission): Try[Unit] = tryWithDb { db =>
    val Permission(id, name, description) = permission

    val permissionDoc = db.newInstance(PermissionClassName)
    permissionDoc.field(Fields.ID, id)
    permissionDoc.field(Fields.Name, name)
    permissionDoc.field(Fields.Description, description)
    permissionDoc.save()
  }

  def createRole(role: Role): Try[Unit] = tryWithDb { db =>
    val Role(name, permissions, description) = role

    val orids = Try(permissions.map { id => getPermissionRid(id) }.map { _.get }).get

    val roleDoc = db.newInstance(RoleClassName)
    roleDoc.field(Fields.Name, name)
    roleDoc.field(Fields.Permissions, orids.asJava)
    roleDoc.field(Fields.Description, description)
    roleDoc.save()
  }

  def setUserRoles(username: String, domainFqn: DomainFqn, roles: List[String]): Try[Unit] = tryWithDb { db =>
    val userOrid = getUserRid(username).get
    val domainOrid = getDomainRid(domainFqn.namespace, domainFqn.domainId).get
    val roleOrids = roles.map { getRolesRid(_).get }

    // TODO: Do these two steps in a transaction
    // Delete roles for that user
    val queryString =
      """DELETE FROM UserDomainRole
        |WHERE
        |  user.username = :username AND
        |  domain.namespace = :namespace AND
        |  domain.id = :domainId""".stripMargin

    val command = new OCommandSQL(queryString)
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    db.command(command).execute(params.asJava)

    // Add new roles
    roleOrids.foreach {
      roleOrid =>
        val userDomainRoleDoc = db.newInstance(UserDomainRoleClassName)
        userDomainRoleDoc.field(Fields.User, userOrid)
        userDomainRoleDoc.field(Fields.Domain, domainOrid)
        userDomainRoleDoc.field(Fields.Role, roleOrid)
        userDomainRoleDoc.save()
    }
  }

  def getAllUserPermissions(username: String, domainFqn: DomainFqn): Try[Set[Permission]] = tryWithDb { db =>
    // TODO: determine how to create a set of permissions in the query
    val queryString =
      """SELECT expand(set(role.permissions))
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    val resultList = results.asScala.toList
    resultList.map { docToPermission(_) }.toSet
  }

  def getUserRolePermissions(username: String, domainFqn: DomainFqn): Try[Set[Role]] = tryWithDb { db =>
    val queryString =
      """SELECT expand(role)
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    val resultList = results.asScala.toSet
    resultList.map { docToRole(_) }
  }

  def getAllUserRoles(domainFqn: DomainFqn): Try[Set[UserRoles]] = tryWithDb { db =>
    val queryString =
      """SELECT user.username, set(role.name) AS roles
        |  FROM UserDomainRole
        |  WHERE domain.namespace = :namespace AND
        |    domain.id = :domainId 
        |  GROUP BY user.username""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    val resultList = results.asScala.toSet
    resultList.map {
      result =>
        val user: String = result.field("user")
        val roles: HashSet[String] = result.field("roles")
        UserRoles(user, roles.asScala.toSet)
    }
  }

  def getUserRoles(username: String, domainFqn: DomainFqn): Try[UserRoles] = tryWithDb { db =>
    val queryString =
      """SELECT role.name as name
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    val resultList = results.asScala.toSet
    UserRoles(username, resultList.map { result => result.field("name").asInstanceOf[String] })
  }

  def getPermissionRid(id: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(PermissionIndex, id, db).get
  }

  def getRolesRid(name: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(RoleIndex, name, db).get
  }

  def getUserRid(username: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(UsernameIndex, username, db).get
  }

  def getDomainRid(namespace: String, domainId: String): Try[ORID] = tryWithDb { db =>
    val key = new OCompositeKey(List(namespace, domainId).asJava)
    QueryUtil.getRidFromIndex(DomainNamespaceIdIndex, key, db).get
  }
}
