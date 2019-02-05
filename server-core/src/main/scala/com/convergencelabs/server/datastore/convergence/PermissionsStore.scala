package com.convergencelabs.server.datastore.convergence

import java.util.HashSet
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.PermissionClass
import com.convergencelabs.server.datastore.convergence.schema.RoleClass
import com.convergencelabs.server.datastore.convergence.schema.UserRoleClass
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging
import scala.util.Success
import com.convergencelabs.server.datastore.convergence.schema.RoleClass
import com.convergencelabs.server.datastore.convergence.schema.DomainClass
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import scala.util.Failure
import com.convergencelabs.server.datastore.DuplicateValueException

sealed trait PermissionTarget
case class DomainPermissionTarget(domainFqn: DomainFqn) extends PermissionTarget
case class NamespacePermissionTarget(id: String) extends PermissionTarget
case object GlobalPermissionTarget extends PermissionTarget

object PermissionsStore {

  case class Permission(id: String, name: String, description: String)
  case class Role(name: String, permissions: List[String], description: String)
  case class UserRoles(username: String, roles: Set[String])

  object Params {
    val Id = "id"
    val Name = "name"
    val Description = "description"

    val Permissions = "permissions"
    val Namespace = "namespace"

    val User = "user"
    val Username = "username"
    val Target = "target"
    val Role = "role"
  }

  def docToPermission(doc: ODocument): Permission = {
    Permission(
      doc.getProperty(PermissionClass.Fields.Id),
      doc.getProperty(PermissionClass.Fields.Name),
      doc.getProperty(PermissionClass.Fields.Description))
  }

  def docToRole(doc: ODocument): Role = {
    val permissionDocs: JavaList[ODocument] = doc.getProperty(RoleClass.Fields.Permissions)
    val permissions = permissionDocs.asScala.map { permisionDoc =>
      val permission: String = permisionDoc.getProperty(PermissionClass.Fields.Id)
      permission
    }.toList

    Role(
      doc.getProperty(RoleClass.Fields.Name),
      permissions,
      doc.getProperty(RoleClass.Fields.Description))
  }

  def buildTargetWhere(target: PermissionTarget): (String, Map[String, Any]) = {
    target match {
      case DomainPermissionTarget(fqn) =>
        val whereClause = "target IN (SELECT FROM Domain WHERE namespace.id = :target_namespace AND id = :target_id)"
        val params = Map("target_namespace" -> fqn.namespace, "target_id" -> fqn.domainId)
        (whereClause, params)
      case NamespacePermissionTarget(id) =>
        val whereClause = "target IN (SELECT FROM Namespace WHERE id = :target_id)"
        val params = Map("target_id" -> id)
        (whereClause, params)
      case GlobalPermissionTarget =>
        ("target IS NULL", Map.empty)
    }
  }

  def selectTarget(target: PermissionTarget, db: ODatabaseDocument): Try[Option[ORID]] = {
    target match {
      case DomainPermissionTarget(domainFqn) =>
        DomainStore.getDomainRid(domainFqn, db).map(Some(_))
      case NamespacePermissionTarget(id) =>
        NamespaceStore.getNamespaceRid(id, db).map(Some(_))
      case GlobalPermissionTarget =>
        Success(None)
    }
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
    db.getMetadata.getIndexManager.getIndex(PermissionClass.Indices.Id).getSize > 0
  }

  def createPermission(permission: Permission): Try[Unit] = tryWithDb { db =>
    val Permission(id, name, description) = permission

    val permissionDoc: ODocument = db.newInstance(PermissionClass.ClassName)
    permissionDoc.setProperty(PermissionClass.Fields.Id, id)
    permissionDoc.setProperty(PermissionClass.Fields.Name, name)
    permissionDoc.setProperty(PermissionClass.Fields.Description, description)
    permissionDoc.save()
    ()
  }.recoverWith(handleDuplicateValue)

  def createRole(role: Role): Try[Unit] = withDb { db =>
    val Role(name, permissions, description) = role
    Try(permissions.map { id => getPermissionRid(id, db) }.map { _.get }).map { orids =>
      val roleDoc: ODocument = db.newInstance(RoleClass.ClassName)
      roleDoc.setProperty(RoleClass.Fields.Name, name)
      roleDoc.setProperty(RoleClass.Fields.Permissions, orids.asJava)
      roleDoc.setProperty(RoleClass.Fields.Description, description)
      roleDoc.save()
      ()
    }
  }.recoverWith(handleDuplicateValue)

  def setUserRolesForTarget(username: String, target: PermissionTarget, roles: List[String]): Try[Unit] = withDb { db =>
    val userOrid = UserStore.getUserRid(username, db).get
    val roleOrids = roles.map { getRolesRid(_, db).get }

    // FIXME: Do these two steps in a transaction

    for {
      targetRid <- selectTarget(target, db)
      _ <- targetRid match {
        case Some(rid) =>
          val query = s"DELETE FROM UserRole WHERE user.username = :username AND target = :target"
          val params = Map(Params.Username -> username, Params.Target -> rid)
          OrientDBUtil.command(db, query, params)
        case None =>
          val query = s"DELETE FROM UserRole WHERE user.username = :username AND target IS NULL"
          val params = Map(Params.Username -> username)
          OrientDBUtil.command(db, query, params)
      }
      _ <- Try {
        roleOrids.foreach { roleOrid =>
          val userRoleDoc: ODocument = db.newInstance(UserRoleClass.ClassName)
          userRoleDoc.setProperty(UserRoleClass.Fields.User, userOrid)
          targetRid.foreach(t => userRoleDoc.setProperty(UserRoleClass.Fields.Target, t))
          userRoleDoc.setProperty(UserRoleClass.Fields.Role, roleOrid)
          userRoleDoc.save()
        }
      }
    } yield (())
  }

  def getUserPermissionsForTarget(username: String, target: PermissionTarget): Try[Set[Permission]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT 
        |  expand(set(role.permissions))
        |FROM
        |  UserRole
        |WHERE
        |  user.username = :username AND
        |  ${targetWhere}""".stripMargin
    val params = Map("username" -> username) ++ targetParams
    OrientDBUtil.query(db, query, params).map(_.map(docToPermission(_)).toSet)
  }

  def getUserRolesForTarget(username: String, target: PermissionTarget): Try[Set[Role]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT
        |   expand(set(role))
        |FROM 
        |  UserRole
        |WHERE 
        |  user.username = :username AND
        |  ${targetWhere}""".stripMargin
    val params = Map("username" -> username) ++ targetParams
    OrientDBUtil.query(db, query, params).map(_.map(docToRole(_)).toSet)
  }

  def getAllUserRolesForTarget(target: PermissionTarget): Try[Set[UserRoles]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT 
        |  user.username as username, set(role.name) AS roles
        |FROM
        |  UserRole
        |WHERE 
        |  ${targetWhere}
        |GROUP BY
        |  user.username""".stripMargin
    OrientDBUtil.query(db, query, targetParams).map(_.map(result => {
      val user: String = result.getProperty("username")
      val roles: HashSet[String] = result.getProperty("roles")
      UserRoles(user, roles.asScala.toSet)
    }).toSet)
  }

  private[this] def getPermissionRid(id: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, PermissionClass.Indices.Id, id)
  }

  private[this] def getRolesRid(name: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, RoleClass.Indices.Name, name)
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case RoleClass.Indices.Name =>
          Failure(DuplicateValueException(RoleClass.Fields.Name))
        case PermissionClass.Indices.Id =>
          Failure(DuplicateValueException(PermissionClass.Fields.Id))
        case PermissionClass.Indices.Name =>
          Failure(DuplicateValueException(PermissionClass.Fields.Name))
        case UserRoleClass.Indices.UserRoleTarget =>
          Failure(DuplicateValueException(s"${UserRoleClass.Fields.User}_${UserRoleClass.Fields.Role}_${UserRoleClass.Fields.Target}"))
        case _ =>
          Failure(e)
      }
  }
}
