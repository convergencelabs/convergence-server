/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

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