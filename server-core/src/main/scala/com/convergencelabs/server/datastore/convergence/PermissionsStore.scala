package com.convergencelabs.server.datastore.convergence

import java.util.HashSet
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object PermissionsStore {

  case class Permission(id: String, name: String, description: String)
  case class Role(name: String, permissions: List[String], description: String)
  case class UserRoles(username: String, roles: Set[String])

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
  import PermissionsStore._

  def hasBeenSetup() = tryWithDb { db =>
    db.getMetadata.getIndexManager.getIndex(Schema.Permission.Indices.Id).getSize > 0
  }

  def createPermission(permission: Permission): Try[Unit] = tryWithDb { db =>
    val Permission(id, name, description) = permission

    val permissionDoc: ODocument = db.newInstance(Schema.Permission.Class)
    permissionDoc.setProperty(Fields.ID, id)
    permissionDoc.setProperty(Fields.Name, name)
    permissionDoc.setProperty(Fields.Description, description)
    permissionDoc.save()
  }

  def createRole(role: Role): Try[Unit] = tryWithDb { db =>
    val Role(name, permissions, description) = role

    val orids = Try(permissions.map { id => getPermissionRid(id) }.map { _.get }).get

    val roleDoc: ODocument = db.newInstance(Schema.Role.Class)
    roleDoc.setProperty(Fields.Name, name)
    roleDoc.setProperty(Fields.Permissions, orids.asJava)
    roleDoc.setProperty(Fields.Description, description)
    roleDoc.save()
  }

  def setUserRoles(username: String, domainFqn: DomainFqn, roles: List[String]): Try[Unit] = tryWithDb { db =>
    val userOrid = getUserRid(username).get
    val domainOrid = getDomainRid(domainFqn.namespace, domainFqn.domainId).get
    val roleOrids = roles.map { getRolesRid(_).get }

    // TODO: Do these two steps in a transaction
    // Delete roles for that user
    val query =
      """DELETE FROM UserDomainRole
        |WHERE
        |  user.username = :username AND
        |  domain.namespace = :namespace AND
        |  domain.id = :domainId""".stripMargin

    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    OrientDBUtil.command(db, query, params)

    // Add new roles
    roleOrids.foreach {
      roleOrid =>
        val userDomainRoleDoc: ODocument = db.newInstance(Schema.UserDomainRole.Class)
        userDomainRoleDoc.setProperty(Fields.User, userOrid)
        userDomainRoleDoc.setProperty(Fields.Domain, domainOrid)
        userDomainRoleDoc.setProperty(Fields.Role, roleOrid)
        userDomainRoleDoc.save()
    }
  }

  def getAllUserPermissions(username: String, domainFqn: DomainFqn): Try[Set[Permission]] = withDb { db =>
    val query =
      """SELECT expand(set(role.permissions))
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(docToPermission(_)).toSet)
  }

  def getUserRolePermissions(username: String, domainFqn: DomainFqn): Try[Set[Role]] = withDb { db =>
    val query =
      """SELECT expand(role)
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(docToRole(_)).toSet)
  }

  def getAllUserRoles(domainFqn: DomainFqn): Try[Set[UserRoles]] = withDb { db =>
    val query =
      """SELECT user.username, set(role.name) AS roles
        |  FROM UserDomainRole
        |  WHERE domain.namespace = :namespace AND
        |    domain.id = :domainId 
        |  GROUP BY user.username""".stripMargin
    val params = Map("namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    OrientDBUtil
      .query(db, query, params)
      .map(_.map(result => {
        val user: String = result.field("user")
        val roles: HashSet[String] = result.field("roles")
        UserRoles(user, roles.asScala.toSet)
      }).toSet)
  }

  def getUserRoles(username: String, domainFqn: DomainFqn): Try[UserRoles] = withDb { db =>
    val query =
      """SELECT role.name as name
        |  FROM UserDomainRole
        |  WHERE user.username = :username AND
        |    domain.namespace = :namespace AND
        |    domain.id = :domainId""".stripMargin
    val params = Map("username" -> username, "namespace" -> domainFqn.namespace, "domainId" -> domainFqn.domainId)
    OrientDBUtil
      .query(db, query, params)
      .map(list => UserRoles(username, list.map(result => result.getProperty("name").asInstanceOf[String]).toSet))
  }

  def getPermissionRid(id: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Permission.Indices.Id, id)
  }

  def getRolesRid(name: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Role.Indices.Id, name)
  }

  def getUserRid(username: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.User.Indices.Username, username)
  }

  def getDomainRid(namespace: String, domainId: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Domain.Indices.NamespaceId, List(namespace, domainId))
  }
}
