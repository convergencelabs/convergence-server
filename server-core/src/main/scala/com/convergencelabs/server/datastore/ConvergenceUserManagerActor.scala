package com.convergencelabs.server.datastore

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import collection.JavaConverters._

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout

object ConvergenceUserManagerActor {
  def props(dbPool: OPartitionedDatabasePool, domainStoreActor: ActorRef): Props =
    Props(new ConvergenceUserManagerActor(dbPool, domainStoreActor))

  case class CreateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, password: String)
  case class DeleteConvergenceUserRequest(username: String)
}

class ConvergenceUserManagerActor private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] val domainStoreActor: ActorRef)
    extends StoreActor
    with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val autoCreateConfigs: List[Config] = context.system.settings.config.getConfigList("convergence.auto-create-domains").asScala.toList
  private[this] val tokenDuration = context.system.settings.config.getDuration("convergence.rest.auth-token-expiration")
  private[this] val userStore: UserStore = new UserStore(dbPool, tokenDuration)

  def receive: Receive = {
    case message: CreateConvergenceUserRequest => createConvergenceUser(message)
    case message: DeleteConvergenceUserRequest => deleteConvergenceUser(message)
    case message: Any => unhandled(message)
  }

  def createConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, password) = message
    val origSender = sender
    userStore.createUser(User(null, username, email, firstName, lastName), password) map {
      case CreateSuccess(uid) => {
        autoCreateConfigs foreach { config => {
          val importFile = if(config.hasPath("import-file")) { Some(config.getString("import-file"))} else {None}
          createDomain(uid, username, config.getString("name"), importFile)}
        }
        origSender ! CreateSuccess(uid)
      }
      case DuplicateValue => origSender ! DuplicateValue
      case InvalidValue => origSender ! InvalidValue
    }
  }

  def deleteConvergenceUser(message: DeleteConvergenceUserRequest): Unit = {
    val origSender = sender
    userStore.deleteUser(message.username) map {
      case DeleteSuccess => {
        // FIXME: Delete all domains for that user
        origSender ! DeleteSuccess
      }
      case NotFound => origSender ! NotFound
    }
  }

  private[this] def createDomain(userId: String, username: String, name: String, importFile: Option[String]): Unit = {
    (domainStoreActor ? CreateDomainRequest(username, name, name, userId, importFile)).mapTo[CreateResult[Unit]] onComplete {
       case Success(resp: CreateSuccess[Unit]) => log.debug(s"Domain '${name}' created for '${username}'");
       case Success(DuplicateValue) => log.error(s"Unable to create '${name}' domain for user: Duplicate value exception");
       case Success(InvalidValue) => log.error(s"Unable to create '${name}' domain for user: Invalid value exception");
       case Failure(f) => log.error(f, s"Unable to create '${name}' domain for user");
    }
    Unit
  }
}
