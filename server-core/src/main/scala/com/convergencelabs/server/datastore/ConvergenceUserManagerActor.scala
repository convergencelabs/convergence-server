package com.convergencelabs.server.datastore

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.DeleteConvergenceUserRequest
import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import scala.util.Success

object ConvergenceUserManagerActor {
  def props(dbPool: OPartitionedDatabasePool, domainStoreActor: ActorRef): Props =
    Props(new ConvergenceUserManagerActor(dbPool, domainStoreActor))

  case class CreateConvergenceUserRequest(username: String, email: String, firstName: String, lastName: String, password: String)
  case class DeleteConvergenceUserRequest(username: String)
}

class ConvergenceUserManagerActor private[datastore] (
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainStoreActor: ActorRef) extends StoreActor with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val domainConfig: Config = context.system.settings.config.getConfig("convergence.domain-databases")
  private[this] val tokenDuration = context.system.settings.config.getDuration("convergence.rest.auth-token-expiration")
  private[this] val userStore: UserStore = new UserStore(dbPool, tokenDuration)

  def receive: Receive = {
    case message: CreateConvergenceUserRequest => createConvergenceUser(message)
    case message: DeleteConvergenceUserRequest => deleteConvergenceUser(message)
    case message: Any                          => unhandled(message)
  }

  def createConvergenceUser(message: CreateConvergenceUserRequest): Unit = {
    val CreateConvergenceUserRequest(username, email, firstName, lastName, password) = message
    val userId = UUID.randomUUID().toString()
    val origSender = sender
    userStore.createUser(User(userId, username, email, firstName, lastName), password) map {
      case CreateSuccess(uid) => {
        val domainResults = for {
          exampleDomain <- createExampleDomain(uid, username)
          defaultDomain <- createDefaultDomain(uid, username)
        } yield (exampleDomain, defaultDomain)

        domainResults onSuccess {
          case (resp1: CreateSuccess[Unit], resp2: CreateSuccess[Unit]) => {
            val blah = origSender
            origSender ! CreateSuccess(Unit)
          }
        }
      }
      case DuplicateValue => origSender ! DuplicateValue
      case InvalidValue   => origSender ! InvalidValue
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

  private[this] def createExampleDomain(userId: String, username: String): Future[CreateResult[Unit]] = {
    (domainStoreActor ? CreateDomainRequest(username, "examples", "Examples", userId, Some("test-server/n1-d1.json.gz"))).mapTo[CreateResult[Unit]]
  }

  private[this] def createDefaultDomain(userId: String, username: String): Future[CreateResult[Unit]] = {
    (domainStoreActor ? CreateDomainRequest(username, "default", "Default", userId, None)).mapTo[CreateResult[Unit]]
  }
}
