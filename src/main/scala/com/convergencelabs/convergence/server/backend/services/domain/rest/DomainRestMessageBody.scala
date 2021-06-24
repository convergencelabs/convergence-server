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

package com.convergencelabs.convergence.server.backend.services.domain.rest

import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.collection.CollectionStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.config.ConfigStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.group.UserGroupStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.jwt.JwtAuthKeyStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelPermissionsStoreActor, ModelServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.session.SessionStoreActor
import com.convergencelabs.convergence.server.backend.services.domain.stats.DomainStatsActor
import com.convergencelabs.convergence.server.backend.services.domain.user.DomainUserStoreActor

object DomainRestMessageBody {

  object Model {
    def apply(msg: ModelServiceActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(model = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ModelServiceActor.Message] = {
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
    def apply(msg: ChatServiceActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(chat = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ChatServiceActor.Message] = {
      arg.chat
    }
  }

  object Activity {
    def apply(msg: ActivityServiceActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(activity = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[ActivityServiceActor.Message] = {
      arg.activity
    }
  }

  object User {
    def apply(msg: DomainUserStoreActor.Message): DomainRestMessageBody =
      DomainRestMessageBody(user = Some(msg))

    def unapply(arg: DomainRestMessageBody): Option[DomainUserStoreActor.Message] = {
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

case class DomainRestMessageBody private(model: Option[ModelServiceActor.Message] = None,
                                         modelPermission: Option[ModelPermissionsStoreActor.Message] = None,
                                         collection: Option[CollectionStoreActor.Message] = None,
                                         chat: Option[ChatServiceActor.Message] = None,
                                         activity: Option[ActivityServiceActor.Message] = None,
                                         user: Option[DomainUserStoreActor.Message] = None,
                                         group: Option[UserGroupStoreActor.Message] = None,
                                         jwtKey: Option[JwtAuthKeyStoreActor.Message] = None,
                                         session: Option[SessionStoreActor.Message] = None,
                                         config: Option[ConfigStoreActor.Message] = None,
                                         stats: Option[DomainStatsActor.Message] = None,
                                         domain: Option[DomainRestActor.DomainMessage] = None
                                        )
