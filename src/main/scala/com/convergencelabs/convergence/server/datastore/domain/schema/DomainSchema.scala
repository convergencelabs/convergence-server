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

package com.convergencelabs.convergence.server.datastore.domain.schema

object DomainSchema {
  object Classes {
    val DomainSession = DomainSessionClass
    val Permission = PermissionClass
    val JwtAuthKey = JwtAuthKeyClass
    val User = UserClass
    val UserGroup = UserGroupClass
    val UserReconnectToken = UserReconnectTokenClass
    val Model = ModelClass
    val ModelSnapshot = ModelSnapshotClass
    val Collection = CollectionClass
    val ModelPermissions = ModelPermissionsClass
    val ModelUserPermissions = ModelUserPermissionsClass
    val CollectionPermissions = CollectionPermissionsClass
    val CollectionUserPermissions = CollectionUserPermissionsClass
    
    val Chat = ChatClass
    val ChatMember = ChatMemberClass
    val ChatEvent = ChatEventClass
    val ChatCreatedEvent = ChatCreatedEventClass
    val ChatNameChangedEvent = ChatNameChangedEventClass
    val ChatTopicChangedEvent = ChatTopicChangedEventClass
    val ChatUserJoinedEvent = ChatUserJoinedEventClass
    val ChatUserLeftEvent = ChatUserLeftEventClass
    val ChatUserAddedEvent = ChatUserAddedEventClass
    val ChatUserRemovedEvent = ChatUserRemovedEventClass
    val ChatMessageEvent = ChatMessageEventClass
  }

  object Sequences {
    val AnonymousUsername = "anonymousUsernameSeq"
    val ChatId = "chatIdSeq"
    val SessionSeq = "SESSIONSEQ"
  }
}