package com.convergencelabs.server.datastore.convergence

import java.util.HashSet
import java.util.{ List => JavaList }

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters._
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

object RoleTargetType extends Enumeration {
  val Namespace, Domain = Value
}

sealed trait RoleTarget {
  def targetClass: Option[RoleTargetType.Value]
}
case class DomainRoleTarget(domainFqn: DomainFqn) extends RoleTarget {
  val targetClass = Some(RoleTargetType.Domain)
}
case class NamespaceRoleTarget(id: String) extends RoleTarget {
  val targetClass = Some(RoleTargetType.Namespace)
}
case object GlobalRoleTarget extends RoleTarget {
  val targetClass = None
}

object RoleStore {

  case class Role(name: String, targetClass: Option[RoleTargetType.Value], permissions: Set[String])
  case class UserRoles(username: String, roles: Set[String])

  object Params {
    val Name = "name"
    val Description = "description"

    val Permissions = "permissions"
    val Namespace = "namespace"

    val User = "user"
    val Username = "username"
    val Target = "target"
    val Role = "role"
  }

  def docToRole(doc: ODocument): Role = {
    val permissions = doc.getProperty(RoleClass.Fields.Permissions).asInstanceOf[JavaList[String]].asScala.toSet
    Role(
      doc.getProperty(RoleClass.Fields.Name),
      Option(doc.getProperty(RoleClass.Fields.TargetClass)).map(RoleTargetType.withName(_)),
      permissions)
  }

  def buildTargetWhere(target: RoleTarget): (String, Map[String, Any]) = {
    target match {
      case DomainRoleTarget(fqn) =>
        val whereClause = "target IN (SELECT FROM Domain WHERE namespace.id = :target_namespace AND id = :target_id)"
        val params = Map("target_namespace" -> fqn.namespace, "target_id" -> fqn.domainId)
        (whereClause, params)
      case NamespaceRoleTarget(id) =>
        val whereClause = "target IN (SELECT FROM Namespace WHERE id = :target_id)"
        val params = Map("target_id" -> id)
        (whereClause, params)
      case GlobalRoleTarget =>
        ("target IS NULL", Map.empty)
    }
  }

  def selectTarget(target: RoleTarget, db: ODatabaseDocument): Try[Option[ORID]] = {
    target match {
      case DomainRoleTarget(domainFqn) =>
        DomainStore.getDomainRid(domainFqn, db).map(Some(_))
      case NamespaceRoleTarget(id) =>
        NamespaceStore.getNamespaceRid(id, db).map(Some(_))
      case GlobalRoleTarget =>
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
class RoleStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {
  import RoleStore._

  def createRole(role: Role): Try[Unit] = tryWithDb { db =>
    val Role(name, targetClass, permissions) = role
    val roleDoc: ODocument = db.newInstance(RoleClass.ClassName)
    roleDoc.setProperty(RoleClass.Fields.Name, name)
    targetClass.foreach(t => roleDoc.setProperty(RoleClass.Fields.TargetClass, t.toString))
    roleDoc.setProperty(RoleClass.Fields.Permissions, permissions.asJava)
    roleDoc.save()
    ()
  }.recoverWith(handleDuplicateValue)

  def setUserRolesForTarget(username: String, target: RoleTarget, roles: Set[String]): Try[Unit] = withDb { db =>
    val userOrid = UserStore.getUserRid(username, db).get
    val roleOrids = roles.map { getRolesRid(_, target.targetClass, db).get }

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

  def getUserPermissionsForTarget(username: String, target: RoleTarget): Try[Set[String]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT 
        |  set(role.permissions) AS permissions
        |FROM
        |  UserRole
        |WHERE
        |  user.username = :username AND
        |  ${targetWhere}""".stripMargin
    val params = Map("username" -> username) ++ targetParams
    OrientDBUtil.query(db, query, params).map(_.map(_.getProperty(RoleClass.Fields.Permissions)).toSet)
  }

  def getUserRolesForTarget(username: String, target: RoleTarget): Try[Set[Role]] = withDb { db =>
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

  def getAllUserRolesForTarget(target: RoleTarget): Try[Set[UserRoles]] = withDb { db =>
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

  private[this] def getRolesRid(name: String, target: Option[RoleTargetType.Value], db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, RoleClass.Indices.NameTargetClass, List(name, target.map(_.toString).getOrElse(null)))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case RoleClass.Indices.NameTargetClass =>
          Failure(DuplicateValueException(s"${RoleClass.Fields.Name}_${RoleClass.Fields.TargetClass}"))
        case PermissionClass.Indices.Id =>
          Failure(DuplicateValueException(PermissionClass.Fields.Id))
        case UserRoleClass.Indices.UserRoleTarget =>
          Failure(DuplicateValueException(s"${UserRoleClass.Fields.User}_${UserRoleClass.Fields.Role}_${UserRoleClass.Fields.Target}"))
        case _ =>
          Failure(e)
      }
  }
}
