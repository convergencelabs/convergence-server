package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.ConfigStoreActor.GetAnonymousAuth
import com.convergencelabs.server.datastore.ConfigStoreActor.SetAnonymousAuth
import com.convergencelabs.server.datastore.ConfigStoreActor.GetModelSnapshotPolicy
import com.convergencelabs.server.datastore.ConfigStoreActor.SetModelSnapshotPolicy
import com.convergencelabs.server.datastore.domain.DomainConfigStore

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.ModelSnapshotConfig

object ConfigStoreActor {
  def props(store: DomainConfigStore): Props = Props(new ConfigStoreActor(store))

  trait ConfigStoreRequest
  case object GetAnonymousAuth extends ConfigStoreRequest
  case class SetAnonymousAuth(enabled: Boolean) extends ConfigStoreRequest
  case object GetModelSnapshotPolicy extends ConfigStoreRequest
  case class SetModelSnapshotPolicy(policy: ModelSnapshotConfig) extends ConfigStoreRequest
}

class ConfigStoreActor private[datastore] (private[this] val store: DomainConfigStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetAnonymousAuth => getAnonymousAuthEnabled()
    case SetAnonymousAuth(enabled) => setAnonymousAuthEnabled(enabled)
    case GetModelSnapshotPolicy => getModelSnapshotPolicy()
    case SetModelSnapshotPolicy(policy) => setModelSnapshotPolicy(policy)
    case message: Any => unhandled(message)
  }

  def getAnonymousAuthEnabled(): Unit = {
    reply(store.isAnonymousAuthEnabled())
  }
  
  def setAnonymousAuthEnabled(enabled: Boolean): Unit = {
    reply(store.setAnonymousAuthEnabled(enabled))
  }
  
  def getModelSnapshotPolicy(): Unit = {
    reply(store.getModelSnapshotConfig())
  }
  
  def setModelSnapshotPolicy(policy: ModelSnapshotConfig): Unit = {
    reply(store.setModelSnapshotConfig(policy))
  }
}