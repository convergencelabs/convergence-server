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

package com.convergencelabs.convergence.server.datastore.convergence

import com.convergencelabs.convergence.server.datastore.convergence.schema._
import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.DomainId
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object RoleTargetType extends Enumeration {
  val Namespace, Domain = Value
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[DomainRoleTarget], name = "domain"),
    new JsonSubTypes.Type(value = classOf[NamespaceRoleTarget], name = "namespace"),
    new JsonSubTypes.Type(value = classOf[ServerRoleTarget], name = "server")
  )
)
sealed trait RoleTarget {
  def targetClass: Option[RoleTargetType.Value]
}

case class DomainRoleTarget(domainId: DomainId) extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = Some(RoleTargetType.Domain)
}

case class NamespaceRoleTarget(id: String) extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = Some(RoleTargetType.Namespace)
}

case class ServerRoleTarget() extends RoleTarget {
  val targetClass: Option[RoleTargetType.Value] = None
}

object RoleStore {

  case class Role(name: String, targetClass: Option[RoleTargetType.Value], permissions: Set[String])
  case class UserRole(role: Role, target: RoleTarget)
  case class UserRoles(username: String, roles: Set[UserRole])

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
    val permissions = doc.getProperty(RoleClass.Fields.Permissions).asInstanceOf[java.util.Set[String]].asScala.toSet
    Role(
      doc.getProperty(RoleClass.Fields.Name),
      Option(doc.getProperty(RoleClass.Fields.TargetClass).asInstanceOf[String]).map(RoleTargetType.withName),
      permissions)
  }

  def docToUserRole(doc: ODocument): UserRole = {
    val roleDoc = OrientDBUtil.resolveLink(doc.getProperty(UserRoleClass.Fields.Role))
    val role = docToRole(roleDoc)
    val targetLink = doc.getProperty(UserRoleClass.Fields.Target).asInstanceOf[OIdentifiable]
    val targetDoc = OrientDBUtil.resolveOptionalLink(targetLink)
    UserRole(role, docToRoleTarget(targetDoc))
  }

  def docToRoleTarget(targetDoc: Option[ODocument]): RoleTarget = {
    targetDoc.map { d =>
      d.getClassName match {
        case DomainClass.ClassName =>
          val namespace = d.eval(DomainClass.Eval.NamespaceId).asInstanceOf[String]
          val id = d.getProperty(DomainClass.Fields.Id).asInstanceOf[String]
          DomainRoleTarget(DomainId(namespace, id))
        case NamespaceClass.ClassName =>
          val id = d.getProperty(NamespaceClass.Fields.Id).asInstanceOf[String]
          NamespaceRoleTarget(id)
      }
    }.getOrElse(ServerRoleTarget())
  }

  def buildTargetWhere(target: RoleTarget): (String, Map[String, Any]) = {
    target match {
      case DomainRoleTarget(domainId) =>
        val whereClause = "target IN (SELECT FROM Domain WHERE namespace.id = :target_namespace AND id = :target_id)"
        val params = Map("target_namespace" -> domainId.namespace, "target_id" -> domainId.domainId)
        (whereClause, params)
      case NamespaceRoleTarget(id) =>
        val whereClause = "target IN (SELECT FROM Namespace WHERE id = :target_id)"
        val params = Map("target_id" -> id)
        (whereClause, params)
      case ServerRoleTarget() =>
        ("target IS NULL", Map.empty)
    }
  }

  def selectTarget(target: RoleTarget, db: ODatabaseDocument): Try[Option[ORID]] = {
    target match {
      case DomainRoleTarget(domainFqn) =>
        DomainStore.getDomainRid(domainFqn, db).map(Some(_))
      case NamespaceRoleTarget(id) =>
        NamespaceStore.getNamespaceRid(id, db).map(Some(_))
      case ServerRoleTarget() =>
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
 * @param dbProvider The database pool to use.
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

  def getRole(name: String, targetClass: Option[RoleTargetType.Value]): Try[Role] = withDb { db =>
    val target = targetClass.map(_.toString).orNull
    OrientDBUtil
      .getDocumentFromSingleValueIndex(db, RoleClass.Indices.NameTargetClass, List(name, target))
      .map(docToRole)
  }

  def setUserRolesForTarget(target: RoleTarget, userRoles: Map[String, Set[String]]): Try[Unit] = withDb { db =>
    // FIXME do in transaction
    Try {
      userRoles.foreach {
        case (username, roles) => setUserRolesForTarget(username, target, roles).get
      }
    }
  }

  def setUserRolesForTarget(username: String, target: RoleTarget, roles: Set[String]): Try[Unit] = withDb { db =>
    // FIXME: Do these two steps in a transaction

    for {
      userOrid <- UserStore.getUserRid(username, db)
      roleOrids <- Try(roles.map { getRolesRid(_, target.targetClass, db).get })
      targetRid <- selectTarget(target, db)
      _ <- targetRid match {
        case Some(rid) =>
          val query = s"DELETE FROM UserRole WHERE user.username = :username AND target = :target"
          val params = Map(Params.Username -> username, Params.Target -> rid)
          OrientDBUtil.commandReturningCount(db, query, params)
        case None =>
          val query = s"DELETE FROM UserRole WHERE user.username = :username AND target IS NULL"
          val params = Map(Params.Username -> username)
          OrientDBUtil.commandReturningCount(db, query, params)
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
    } yield ()
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
        |  $targetWhere""".stripMargin
    val params = Map(Params.Username -> username) ++ targetParams
    OrientDBUtil.findDocumentAndMap(db, query, params) { doc =>
      doc.getProperty(RoleClass.Fields.Permissions).asInstanceOf[java.util.Set[String]].asScala.toSet
    }.map(_.getOrElse(Set[String]()))
  }

  private[this] val GetAllRolesForUserQuery = "SELECT FROM UserRole WHERE user.username = :username"
  def getAllRolesForUser(username: String): Try[UserRoles] = withDb { db =>
    val params = Map(Params.Username -> username)
    OrientDBUtil.query(db, GetAllRolesForUserQuery, params).map { docs =>
      val roles = docs.map(docToUserRole).toSet
      UserRoles(username, roles)
    }
  }

  def getRolesForUsersAndTarget(usernames: Set[String], target: RoleTarget): Try[Map[String, Set[String]]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT
        |   user.username AS username, target, set(role.name) AS roles
        |FROM 
        |  UserRole
        |WHERE 
        |  user.username IN :usernames AND
        |  $targetWhere
        |GROUP BY
        |  user.username, target""".stripMargin
    val params = Map("usernames" -> usernames.asJava) ++ targetParams
    OrientDBUtil.queryAndMap(db, query, params) { doc =>
      val username: String = doc.getProperty("username")
      val roles = doc.getProperty("roles").asInstanceOf[java.util.Set[String]]
      username -> roles.asScala.toSet
    }.map(_.toMap)
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
        |  $targetWhere""".stripMargin
    val params = Map("username" -> username) ++ targetParams
    OrientDBUtil.query(db, query, params).map(_.map(docToRole).toSet)
  }

  def getAllUserRolesForTarget(target: RoleTarget): Try[Set[UserRoles]] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |SELECT 
        |  user.username AS username, target, set(role) AS roles
        |FROM
        |  UserRole
        |WHERE 
        |  $targetWhere
        |GROUP BY
        |  user.username, target""".stripMargin
    OrientDBUtil.query(db, query, targetParams).map(_.map(result => {
      val user: String = result.getProperty("username")
      val targetDoc: ODocument = result.getProperty("target")
      val target = docToRoleTarget(Option(targetDoc))
      val roleDocs = result.getProperty("roles").asInstanceOf[java.util.Set[ORID]].asScala.toSet
      val roles = roleDocs.map(r => docToRole(r.getRecord.asInstanceOf[ODocument]))
      UserRoles(user, roles.map(r => UserRole(r, target)))
    }).toSet)
  }

  def removeUserRoleFromTarget(target: RoleTarget, username: String): Try[Unit] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"""
        |DELETE 
        |FROM
        |  UserRole
        |WHERE 
        |  user.username = :username AND
        |  $targetWhere""".stripMargin
    val params = Map(Params.Username -> username) ++ targetParams
    OrientDBUtil.commandReturningCount(db, query, params).map(_ => ())
  }

  def removeAllRolesFromTarget(target: RoleTarget): Try[Unit] = withDb { db =>
    val (targetWhere, targetParams) = buildTargetWhere(target)
    val query = s"DELETE FROM UserRole WHERE $targetWhere"
    OrientDBUtil.commandReturningCount(db, query, targetParams).map(_ => ())
  }

  private[this] def getRolesRid(name: String, target: Option[RoleTargetType.Value], db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, RoleClass.Indices.NameTargetClass, List(name, target.map(_.toString).orNull))
  }

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
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
