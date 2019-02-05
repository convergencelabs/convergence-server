package com.convergencelabs.server.datastore.convergence

import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.util.ExceptionUtils
import com.convergencelabs.server.db.DatabaseProvider

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout

object NamespaceStoreActor {
  val RelativePath = "NamespaceStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new NamespaceStoreActor(dbProvider))

  case class CreateNamespace(requestor: String, namespaceId: String, displayName: String)
  case class UpdateNamespace(requestor: String, namespaceId: String, displayName: String)
  case class DeleteNamespace(requestor: String, namespaceId: String)
  case class GetAccessibleNamespaces(requestor: String)
  case class GetNamespacesWithCreatePerimssion(requestor: String)
  
}

class NamespaceStoreActor private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
  extends StoreActor with ActorLogging {

  import NamespaceStoreActor._
  import akka.pattern.ask

  private[this] val namespaceStore = new NamespaceStore(dbProvider)

  def receive: Receive = {
    case createRequest: CreateNamespace => createNamespace(createRequest)
    case deleteRequest: DeleteNamespace => deleteNamespace(deleteRequest)
    case updateRequest: UpdateNamespace => updateNamespace(updateRequest)
    case accessibleRequest: GetAccessibleNamespaces => getAccessibleNamespaces(accessibleRequest)
    case manageableRequest: GetNamespacesWithCreatePerimssion => getNamespaceWithCreate(manageableRequest)
    case message: Any => unhandled(message)
  }

  def createNamespace(createRequest: CreateNamespace): Unit = {
    val CreateNamespace(requestor, namespaceId, displayName) = createRequest
    log.debug(s"Receved request to create Namespace: ${namespaceId}")

  }

  def updateNamespace(request: UpdateNamespace): Unit = {
    val UpdateNamespace(requestor, namespaceId, displayName) = request
    log.debug(s"Receved request to update Namespace: ${namespaceId}")
  }
  
  def deleteNamespace(deleteRequest: DeleteNamespace): Unit = {
    val DeleteNamespace(requestor, namespaceId) = deleteRequest
    log.debug(s"Receved request to delete Namespace: ${namespaceId}")

  }

  def getAccessibleNamespaces(getRequest: GetAccessibleNamespaces): Unit = {
    val GetAccessibleNamespaces(requestor) = getRequest

  }
  
  def getNamespaceWithCreate(getRequest: GetNamespacesWithCreatePerimssion): Unit = {
    val GetNamespacesWithCreatePerimssion(requestor) = getRequest
  }
}
