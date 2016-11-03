package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.domain.DomainUser

import UserStoreActor.CreateUser
import UserStoreActor.GetUsers
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.UserStoreActor.GetUserByUsername
import com.convergencelabs.server.datastore.UserStoreActor.DeleteDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateDomainUser
import com.convergencelabs.server.datastore.UserStoreActor.UpdateUser
import com.convergencelabs.server.datastore.UserStoreActor.SetPassword
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.ConfigStoreActor.GetAnonymousAuth
import com.convergencelabs.server.datastore.ConfigStoreActor.SetAnonymousAuth

object ConfigStoreActor {
  def props(store: DomainConfigStore): Props = Props(new ConfigStoreActor(store))

  trait ConfigStoreRequest
  case class SetAnonymousAuth(enabled: Boolean) extends ConfigStoreRequest
  case object GetAnonymousAuth extends ConfigStoreRequest
}

class ConfigStoreActor private[datastore] (private[this] val store: DomainConfigStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetAnonymousAuth => getAnonymousAuthEnabled()
    case SetAnonymousAuth(enabled) => setAnonymousAuthEnabled(enabled)
    case message: Any => unhandled(message)
  }

  def getAnonymousAuthEnabled(): Unit = {
    reply(store.isAnonymousAuthEnabled())
  }
  
  def setAnonymousAuthEnabled(enabled: Boolean): Unit = {
    reply(store.setAnonymousAuthEnabled(enabled))
  }
}