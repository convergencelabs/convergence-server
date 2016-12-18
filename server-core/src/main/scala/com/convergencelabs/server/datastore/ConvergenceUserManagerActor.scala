package com.convergencelabs.server.datastore

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUser
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.GetConvergenceUsers
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainsForUserRequest
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

object ConvergenceUserManagerActor {
  def props(dbProvider: DatabaseProvider, domainStoreActor: ActorRef): Props =
    Props(new ConvergenceUserManagerActor(dbProvider, domainStoreActor))

  case class CreateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, displayName: String, password: String)
  case class UpdateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, displayName: String)
  case class SetPasswordRequest(username: String, password: String)
  case class DeleteConvergenceUserRequest(username: String)
  case class GetConvergenceUser(username: String)
  case object GetConvergenceUsers
}

class ConvergenceUserManagerActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider,
  private[this] val domainStoreActor: ActorRef)
    extends StoreActor
    with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val autoCreateConfigs: List[Config] = context.system.settings.config.getConfigList("convergence.auto-create-domains").asScala.toList
  private[this] val tokenDuration = context.system.settings.config.getDuration("convergence.rest.auth-token-expiration")
  private[this] val userStore: UserStore = new UserStore(dbProvider, tokenDuration)

  def receive: Receive = {
    case message: CreateConvergenceUserRequest => createConvergenceUser(message)
    case message: DeleteConvergenceUserRequest => deleteConvergenceUser(message)
    case message: GetConvergenceUser => getConvergenceUser(message)
    case GetConvergenceUsers => getConvergenceUsers()
    case message: UpdateConvergenceUserRequest => updateConvergenceUser(message)
    case message: SetPasswordRequest => setUserPassword(message)
    case message: Any => unhandled(message)
    
  }

  def createConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, displayName, password) = message
    val origSender = sender
    userStore.createUser(User(username, email, firstName, lastName, displayName), password) map { _ =>
      log.debug("User created.  Creating domains")
      FutureUtils.seqFutures(autoCreateConfigs) { config =>
        createDomain(username, config.getString("id"), config.getString("displayName"))
      }

      origSender ! (())
    } recover {
      case e: Throwable =>
        origSender ! Status.Failure(e)
    }
  }

  def getConvergenceUser(message: GetConvergenceUser): Unit = {
    val GetConvergenceUser(username) = message
    reply(userStore.getUserByUsername(username))
  }

  def getConvergenceUsers(): Unit = {
    reply(userStore.getUsers())
  }

  def deleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    val DeleteConvergenceUserRequest(username) = message;

    val result = (domainStoreActor ? DeleteDomainsForUserRequest(username)).mapTo[Unit] flatMap (_ =>
      FutureUtils.tryToFuture(userStore.deleteUser(username)))

    reply(result)
  }
  
  def updateConvergenceUser(message: UpdateConvergenceUserRequest): Unit = {
    val UpdateConvergenceUserRequest(username, email, firstName, lastName, displayName) = message;
    log.debug(s"Updating user: ${username}")
    val update = User(username, email, firstName, lastName, displayName)
    reply(userStore.updateUser(update))
  }
  
  def setUserPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password) = message;
    log.debug(s"Setting the password for user: ${username}")
    reply(userStore.setUserPassword(username, password))
  }

  private[this] def createDomain(username: String, id: String, displayName: String): Future[Any] = {
    log.debug(s"Requesting domain creation for user '${username}': $id")

    // FIXME hard coded
    implicit val requstTimeout = Timeout(240 seconds)
    val message = CreateDomainRequest(username, id, displayName, username)
    (domainStoreActor ? message).andThen {
      case Success(_) =>
        log.debug(s"Domain '${id}' created for '${username}'");
      case Failure(f) =>
        log.error(f, s"Unable to create '${id}' domain for user");
    }
  }
}
