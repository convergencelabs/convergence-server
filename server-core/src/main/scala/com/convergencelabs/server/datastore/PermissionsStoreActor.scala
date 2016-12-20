package com.convergencelabs.server.datastore

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.util.concurrent.FutureUtils
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor._
import com.convergencelabs.server.datastore.UserStore.User
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.PermissionsStoreActor.CreatePermissionRequest
import com.convergencelabs.server.datastore.PermissionsStoreActor.CreateRoleRequest
import com.convergencelabs.server.datastore.PermissionsStoreActor.GetPermissionsProfileRequest

object PermissionsStoreActor {
  def props(dbProvider: DatabaseProvider): Props = Props(new PermissionsStoreActor(dbProvider))

  case class CreatePermissionRequest(permission: Permission)
  case class CreateRoleRequest(role: Role)
  case class GetPermissionsProfileRequest(domainFqn: DomainFqn, username: String)
}

class PermissionsStoreActor private[datastore] (private[this] val dbProvider: DatabaseProvider) extends StoreActor
    with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val permissionsStore: PermissionsStore = new PermissionsStore(dbProvider)

  def receive: Receive = {
    case message: CreatePermissionRequest      => createPermission(message)
    case message: CreateRoleRequest            => createRole(message)
    case message: GetPermissionsProfileRequest => getPermissionsProfile(message)
    case message: Any                          => unhandled(message)

  }

  def createPermission(message: CreatePermissionRequest): Unit = {
    val CreatePermissionRequest(permission) = message
    reply(permissionsStore.createPermission(permission))
  }

  def createRole(message: CreateRoleRequest): Unit = {
    val CreateRoleRequest(role) = message
    reply(permissionsStore.createRole(role))
  }

  def getPermissionsProfile(message: GetPermissionsProfileRequest): Unit = {
    val GetPermissionsProfileRequest(domainFqn, username) = message
    reply(permissionsStore.getAllUserRoles(username, domainFqn).map { roles => new PermissionsProfile(roles) })
  }
}
