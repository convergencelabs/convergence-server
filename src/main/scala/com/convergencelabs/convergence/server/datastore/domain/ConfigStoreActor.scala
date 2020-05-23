/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.domain

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody


class ConfigStoreActor private[datastore](private[this] val store: DomainConfigStore)
  extends StoreActor with ActorLogging {

  import ConfigStoreActor._

  def receive: Receive = {
    case GetAnonymousAuthRequest =>
      onGetAnonymousAuthEnabled()
    case SetAnonymousAuthRequest(enabled) =>
      onSetAnonymousAuthEnabled(enabled)
    case GetModelSnapshotPolicyRequest =>
      onGetModelSnapshotPolicy()
    case SetModelSnapshotPolicyRequest(policy) =>
      onSetModelSnapshotPolicy(policy)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetAnonymousAuthEnabled(): Unit = {
    reply(store.isAnonymousAuthEnabled().map(GetAnonymousAuthResponse))
  }

  private[this] def onSetAnonymousAuthEnabled(enabled: Boolean): Unit = {
    reply(store.setAnonymousAuthEnabled(enabled))
  }

  private[this] def onGetModelSnapshotPolicy(): Unit = {
    reply(store.getModelSnapshotConfig().map(GetModelSnapshotPolicyResponse))
  }

  private[this] def onSetModelSnapshotPolicy(policy: ModelSnapshotConfig): Unit = {
    reply(store.setModelSnapshotConfig(policy))
  }
}


object ConfigStoreActor {
  def props(store: DomainConfigStore): Props = Props(new ConfigStoreActor(store))

  sealed trait ConfigStoreRequest extends CborSerializable with DomainRestMessageBody

  case object GetAnonymousAuthRequest extends ConfigStoreRequest

  case class GetAnonymousAuthResponse(enabled: Boolean) extends CborSerializable

  case class SetAnonymousAuthRequest(enabled: Boolean) extends ConfigStoreRequest

  case object GetModelSnapshotPolicyRequest extends ConfigStoreRequest

  case class GetModelSnapshotPolicyResponse(policy: ModelSnapshotConfig) extends CborSerializable

  case class SetModelSnapshotPolicyRequest(policy: ModelSnapshotConfig) extends ConfigStoreRequest

}