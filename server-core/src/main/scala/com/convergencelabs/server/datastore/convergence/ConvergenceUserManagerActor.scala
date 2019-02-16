package com.convergencelabs.server.datastore.convergence

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.util.RandomStringGenerator
import com.convergencelabs.server.util.concurrent.FutureUtils
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.actorRef2Scala
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.DeleteDomainsForUserRequest
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.CreateDomainRequest
import java.time.Instant
import com.convergencelabs.server.security.Roles

object ConvergenceUserManagerActor {
  val RelativePath = "ConvergenceUserManagerActor"

  def props(dbProvider: DatabaseProvider, domainStoreActor: ActorRef): Props =
    Props(new ConvergenceUserManagerActor(dbProvider, domainStoreActor))

  case class CreateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, displayName: String, password: String, globalRole: String)
  case class UpdateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, displayName: String, globalRole: String)
  case class UpdateConvergenceUserProfileRequest(username: String, email: String, firstName: String, lastName: String, displayName: String)
  case class SetPasswordRequest(username: String, password: String)
  case class DeleteConvergenceUserRequest(username: String)

  case class GetConvergenceUsers(filter: Option[String], limit: Option[Int], offset: Option[Int])
  case class GetConvergenceUser(username: String)
  case class ConvergenceUserInfo(user: User, globalRole: String)

  case class GetUserBearerTokenRequest(username: String)
  case class RegenerateUserBearerTokenRequest(username: String)
}

class ConvergenceUserManagerActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider,
  private[this] val domainStoreActor: ActorRef)
  extends StoreActor
  with ActorLogging {

  import ConvergenceUserManagerActor._
  import akka.pattern.ask

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val tokenDuration = context.system.settings.config.getDuration("convergence.rest.session-token-expiration")
  private[this] val userStore: UserStore = new UserStore(dbProvider)
  private[this] val roleStore: RoleStore = new RoleStore(dbProvider)

  private[this] val userCreator = new UserCreator(dbProvider)

  def receive: Receive = {
    case message: CreateConvergenceUserRequest =>
      createConvergenceUser(message)
    case message: DeleteConvergenceUserRequest =>
      deleteConvergenceUser(message)
    case message: GetConvergenceUser =>
      getConvergenceUser(message)
    case message: GetConvergenceUsers =>
      getConvergenceUsers(message)
    case message: UpdateConvergenceUserProfileRequest =>
      updateConvergenceUserProfile(message)
    case message: UpdateConvergenceUserRequest =>
      updateConvergenceUser(message)
    case message: SetPasswordRequest =>
      setUserPassword(message)
    case message: GetUserBearerTokenRequest =>
      getUserBearerToken(message)
    case message: RegenerateUserBearerTokenRequest =>
      regenerateUserBearerToken(message)
    case message: Any =>
      unhandled(message)
  }

  def createConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password, serverRole) = message
    val origSender = sender
    val user = User(username, email, firstName, lastName, displayName, None)
    userCreator.createUser(user, password, serverRole)
      .map(_ => origSender ! (()))
      .recover {
        case e: Throwable =>
          origSender ! Status.Failure(e)
      }
  }

  def getConvergenceUser(message: GetConvergenceUser): Unit = {
    val GetConvergenceUser(username) = message
    val overviews = (for {
      user <- userStore.getUserByUsername(username)
      roles <- roleStore.getRolesForUsersAndTarget(Set(username), ServerRoleTarget)
    } yield {
      user.map { u =>
        val globalRole = roles.get(u.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(u, globalRole)
      }
    })

    reply(overviews)
  }

  def getConvergenceUsers(message: GetConvergenceUsers): Unit = {
    val GetConvergenceUsers(filter, limit, offset) = message
    val overviews = (for {
      users <- userStore.getUsers(filter, limit, offset)
      roles <- roleStore.getRolesForUsersAndTarget(users.map(_.username).toSet, ServerRoleTarget)
    } yield {
      users.map { user =>
        val globalRole = roles.get(user.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserInfo(user, globalRole)
      }.toSet
    })

    reply(overviews)
  }

  def deleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    // FIXME remove favorites
    val DeleteConvergenceUserRequest(username) = message;
    val result = (domainStoreActor ? DeleteDomainsForUserRequest(username))
      .mapTo[Unit]
      .flatMap(_ => FutureUtils.tryToFuture(userStore.deleteUser(username)))
    reply(result)
  }

  def updateConvergenceUserProfile(message: UpdateConvergenceUserProfileRequest): Unit = {
    val UpdateConvergenceUserProfileRequest(username, email, firstName, lastName, displayName) = message;
    log.debug(s"Updating user: ${username}")
    val update = User(username, email, firstName, lastName, displayName, None)
    reply(userStore.updateUser(update))
  }

  def updateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName, globalRole) = message;
    log.debug(s"Updating user: ${username}")
    val update = User(username, email, firstName, lastName, displayName, None)
    reply(userStore.updateUser(update))
  }

  def setUserPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password) = message;
    log.debug(s"Setting the password for user: ${username}")
    reply(userStore.setUserPassword(username, password))
  }

  def getUserBearerToken(message: GetUserBearerTokenRequest): Unit = {
    val GetUserBearerTokenRequest(username) = message;
    val result = userStore.getBearerToken(username);
    reply(result)
  }

  def regenerateUserBearerToken(message: RegenerateUserBearerTokenRequest): Unit = {
    val RegenerateUserBearerTokenRequest(username) = message;
    log.debug(s"Regenerating the api key for user: ${username}")
    val bearerToken = userCreator.bearerTokenGen.nextString()
    reply(userStore.setBearerToken(username, bearerToken).map(_ => bearerToken))
  }

  private[this] def createDomain(username: String, id: String, displayName: String, anonymousAuth: Boolean): Future[Any] = {
    log.debug(s"Requesting domain creation for user '${username}': $id")

    // FIXME hard coded
    implicit val requstTimeout = Timeout(240 seconds)
    val message = CreateDomainRequest(username, id, displayName, anonymousAuth)
    (domainStoreActor ? message).andThen {
      case Success(_) =>
        log.debug(s"Domain '${id}' created for '${username}'");
      case Failure(f) =>
        log.error(f, s"Unable to create '${id}' domain for user");
    }
  }
}
