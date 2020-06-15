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

package com.convergencelabs.convergence.server.domain.rest

import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.chat.ChatManagerActor

object DomainRestMessageBody {

  object Model {
    def apply(msg: ModelStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(model = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ModelStoreActor.Message] = {
      arg.model
    }
  }

  object ModelPermission {
    def apply(msg: ModelPermissionsStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(modelPermission = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ModelPermissionsStoreActor.Message] = {
      arg.modelPermission
    }
  }

  object Collection {
    def apply(msg: CollectionStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(collection = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[CollectionStoreActor.Message] = {
      arg.collection
    }
  }

  object Chat {
    def apply(msg: ChatManagerActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(chat = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ChatManagerActor.Message] = {
      arg.chat
    }
  }

  object User {
    def apply(msg: UserStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(user = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[UserStoreActor.Message] = {
      arg.user
    }
  }

  object Group {
    def apply(msg: UserGroupStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(group = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[UserGroupStoreActor.Message] = {
      arg.group
    }
  }

  object JwtAuthKey {
    def apply(msg: JwtAuthKeyStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(jwtKey = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[JwtAuthKeyStoreActor.Message] = {
      arg.jwtKey
    }
  }

  object Session {
    def apply(msg: SessionStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(session = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[SessionStoreActor.Message] = {
      arg.session
    }
  }

  object Config {
    def apply(msg: ConfigStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(config = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ConfigStoreActor.Message] = {
      arg.config
    }
  }

  object Stats {
    def apply(msg: DomainStatsActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(stats = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[DomainStatsActor.Message] = {
      arg.stats
    }
  }

  object Domain {
    def apply(msg: DomainRestActor.DomainMessage): DomainRestMessageBody =
      DomainRestMessageBody(domain = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[DomainRestActor.DomainMessage] = {
      arg.domain
    }
  }

}

case class DomainRestMessageBody private(model: Option[ModelStoreActor.Message] = None,
                                         modelPermission: Option[ModelPermissionsStoreActor.Message] = None,
                                         collection: Option[CollectionStoreActor.Message] = None,
                                         chat: Option[ChatManagerActor.Message] = None,
                                         user: Option[UserStoreActor.Message] = None,
                                         group: Option[UserGroupStoreActor.Message] = None,
                                         jwtKey: Option[JwtAuthKeyStoreActor.Message] = None,
                                         session: Option[SessionStoreActor.Message] = None,
                                         config: Option[ConfigStoreActor.Message] = None,
                                         stats: Option[DomainStatsActor.Message] = None,
                                         domain: Option[DomainRestActor.DomainMessage] = None
                                        )
