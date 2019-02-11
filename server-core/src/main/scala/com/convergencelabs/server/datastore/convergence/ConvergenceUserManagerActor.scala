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
  case class UpdateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, displayName: String)
  case class SetPasswordRequest(username: String, password: String)
  case class DeleteConvergenceUserRequest(username: String)
  case class GetConvergenceUser(username: String)
  case class GetConvergenceUsers(filter: Option[String], limit: Option[Int], offset: Option[Int])

  case class GetConvergenceUserInfo(filter: Option[String], limit: Option[Int], offset: Option[Int])
  case class ConvergenceUserOverview(user: User, globalRole: String)

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
  private[this] val autoCreateConfigs: List[Config] = context.system.settings.config.getConfigList("convergence.auto-create-domains").asScala.toList
  private[this] val tokenDuration = context.system.settings.config.getDuration("convergence.rest.session-token-expiration")
  private[this] val userStore: UserStore = new UserStore(dbProvider)
  private[this] val roleStore: RoleStore = new RoleStore(dbProvider)
  private[this] val namespaceStore: NamespaceStore = new NamespaceStore(dbProvider)

  private[this] val bearerTokenGen = new RandomStringGenerator(32)

  def receive: Receive = {
    case message: CreateConvergenceUserRequest =>
      createConvergenceUser(message)
    case message: DeleteConvergenceUserRequest =>
      deleteConvergenceUser(message)
    case message: GetConvergenceUser =>
      getConvergenceUser(message)
    case message: GetConvergenceUsers =>
      getConvergenceUsers(message)
    case message: GetConvergenceUserInfo =>
      getConvergenceUserOverviews(message)
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
    val CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password, globalRole) = message
    val origSender = sender
    val bearerToken = bearerTokenGen.nextString()

    (for {
      _ <- userStore.createUser(User(username, email, firstName, lastName, displayName, None), password, bearerToken)
      _ <- roleStore.setUserRolesForTarget(username, ServerRoleTarget, Set(globalRole))
      namespace <- namespaceStore.createUserNamespace(username)
      _ <- roleStore.setUserRolesForTarget(username, NamespaceRoleTarget(namespace), Set(Roles.Namespace.Owner))
    } yield {
      origSender ! (())

      if (autoCreateConfigs.size > 0) {
        log.debug("User namespace created.  Creating domains ")
        FutureUtils.seqFutures(autoCreateConfigs) { config =>
          createDomain(
            namespace,
            config.getString("id"),
            config.getString("displayName"),
            config.getBoolean("anonymousAuth"))
        }
      }
    }) recover {
      case e: Throwable =>
        origSender ! Status.Failure(e)
    }
  }

  def getConvergenceUser(message: GetConvergenceUser): Unit = {
    val GetConvergenceUser(username) = message
    reply(userStore.getUserByUsername(username))
  }

  def getConvergenceUsers(message: GetConvergenceUsers): Unit = {
    val GetConvergenceUsers(filter, limit, offset) = message
    reply(userStore.getUsers(filter, limit, offset))
  }

  def getConvergenceUserOverviews(message: GetConvergenceUserInfo): Unit = {
    val GetConvergenceUserInfo(filter, limit, offset) = message
    val overviews = (for {
      users <- userStore.getUsers(filter, limit, offset)
      roles <- roleStore.getRolesForUsersAndTarget(users.map(_.username).toSet, ServerRoleTarget)
    } yield {
      users.map { user =>
        val globalRole = roles.get(user.username).flatMap(_.headOption).getOrElse("")
        ConvergenceUserOverview(user, globalRole)
      }.toSet
    })

    reply(overviews)
  }

  def deleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    val DeleteConvergenceUserRequest(username) = message;
    val result = (domainStoreActor ? DeleteDomainsForUserRequest(username))
      .mapTo[Unit]
      .flatMap(_ => FutureUtils.tryToFuture(userStore.deleteUser(username)))
    reply(result)
  }

  def updateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName) = message;
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
    val bearerToken = bearerTokenGen.nextString()
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
