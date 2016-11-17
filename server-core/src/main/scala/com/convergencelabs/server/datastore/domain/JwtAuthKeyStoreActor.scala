package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore.KeyInfo

import JwtAuthKeyStoreActor.CreateDomainApiKey
import JwtAuthKeyStoreActor.DeleteDomainApiKey
import JwtAuthKeyStoreActor.GetDomainApiKey
import JwtAuthKeyStoreActor.GetDomainApiKeys
import JwtAuthKeyStoreActor.UpdateDomainApiKey
import akka.actor.ActorLogging
import akka.actor.Props

object JwtAuthKeyStoreActor {
  def props(keyStore: JwtAuthKeyStore): Props = Props(new JwtAuthKeyStoreActor(keyStore))

  sealed trait ApiKeyStoreRequest
  case class GetDomainApiKeys(offset: Option[Int], limit: Option[Int]) extends ApiKeyStoreRequest
  case class GetDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class DeleteDomainApiKey(id: String) extends ApiKeyStoreRequest
  case class UpdateDomainApiKey(key: KeyInfo) extends ApiKeyStoreRequest
  case class CreateDomainApiKey(key: KeyInfo) extends ApiKeyStoreRequest
}

class JwtAuthKeyStoreActor private[datastore] (private[this] val keyStore: JwtAuthKeyStore)
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

  def createKey(key: KeyInfo): Unit = {
    reply(keyStore.createKey(key))
  }

  def updateKey(key: KeyInfo): Unit = {
    reply(keyStore.updateKey(key))
  }
}
