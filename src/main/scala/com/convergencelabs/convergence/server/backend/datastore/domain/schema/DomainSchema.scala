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

package com.convergencelabs.convergence.server.backend.datastore.domain.schema

object DomainSchema {
  object Classes {
    val DomainSession: DomainSessionClass.type = DomainSessionClass
    val DomainConfig: DomainConfigClass.type = DomainConfigClass
    val Permission: PermissionClass.type = PermissionClass
    val JwtAuthKey: JwtAuthKeyClass.type = JwtAuthKeyClass
    val User: UserClass.type = UserClass
    val UserCredential: UserCredentialClass.type = UserCredentialClass
    val UserGroup: UserGroupClass.type = UserGroupClass
    val UserReconnectToken: UserReconnectTokenClass.type = UserReconnectTokenClass
    val Model: ModelClass.type = ModelClass
    val ModelSnapshot: ModelSnapshotClass.type = ModelSnapshotClass
    val Collection: CollectionClass.type = CollectionClass
    val ModelPermissions: ModelPermissionsClass.type = ModelPermissionsClass
    val ModelUserPermissions: ModelUserPermissionsClass.type = ModelUserPermissionsClass
    val CollectionPermissions: CollectionPermissionsClass.type = CollectionPermissionsClass
    val CollectionUserPermissions: CollectionUserPermissionsClass.type = CollectionUserPermissionsClass
    
    val Chat: ChatClass.type = ChatClass
    val ChatMember: ChatMemberClass.type = ChatMemberClass
    val ChatEvent: ChatEventClass.type = ChatEventClass
    val ChatCreatedEvent: ChatCreatedEventClass.type = ChatCreatedEventClass
    val ChatNameChangedEvent: ChatNameChangedEventClass.type = ChatNameChangedEventClass
    val ChatTopicChangedEvent: ChatTopicChangedEventClass.type = ChatTopicChangedEventClass
    val ChatUserJoinedEvent: ChatUserJoinedEventClass.type = ChatUserJoinedEventClass
    val ChatUserLeftEvent: ChatUserLeftEventClass.type = ChatUserLeftEventClass
    val ChatUserAddedEvent: ChatUserAddedEventClass.type = ChatUserAddedEventClass
    val ChatUserRemovedEvent: ChatUserRemovedEventClass.type = ChatUserRemovedEventClass
    val ChatMessageEvent: ChatMessageEventClass.type = ChatMessageEventClass
  }

  object Sequences {
    val AnonymousUsername = "anonymousUsernameSeq"
    val ChatId = "chatIdSeq"
    val SessionSeq = "SESSIONSEQ"
  }
}