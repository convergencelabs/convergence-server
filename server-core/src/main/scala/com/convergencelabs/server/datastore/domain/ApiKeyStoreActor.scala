package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.ApiKeyStore
import com.convergencelabs.server.domain.TokenPublicKey

import ApiKeyStoreActor.CreateDomainApiKey
import ApiKeyStoreActor.DeleteDomainApiKey
import ApiKeyStoreActor.GetDomainApiKey
import ApiKeyStoreActor.GetDomainApiKeys
import ApiKeyStoreActor.UpdateDomainApiKey
import akka.actor.ActorLogging
import akka.actor.Props

object ApiKeyStoreActor {
  def props(keyStore: ApiKeyStore): Props = Props(new ApiKeyStoreActor(keyStore))

  sealed trait ApiKeyStoreRequest
  case class GetDomainApiKeys(offset: Option[Int], limit: Option[Int]) extends ApiKeyStoreRequest
  case class GetDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class DeleteDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class UpdateDomainApiKey(key: TokenPublicKey) extends ApiKeyStoreRequest
  case class CreateDomainApiKey(key: TokenPublicKey) extends ApiKeyStoreRequest
}

class ApiKeyStoreActor private[datastore] (private[this] val keyStore: ApiKeyStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetDomainApiKeys(offset, limit) => getKeys(offset, limit)
    case GetDomainApiKey(id) => getKey(id)
    case DeleteDomainApiKey(id) => deleteKey(id)
    case CreateDomainApiKey(key) => createKey(key)
    case UpdateDomainApiKey(key) => updateKey(key)
    case message: Any => unhandled(message)
  }

  def getKeys(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(keyStore.getKeys(offset, limit))
  }

  def getKey(id: String): Unit = {
    reply(keyStore.getKey(id))
  }

  def deleteKey(id: String): Unit = {
    reply(keyStore.deleteKey(id))
  }

  def createKey(key: TokenPublicKey): Unit = {
    reply(keyStore.createKey(key))
  }

  def updateKey(key: TokenPublicKey): Unit = {
    reply(keyStore.updateKey(key))
  }
}
