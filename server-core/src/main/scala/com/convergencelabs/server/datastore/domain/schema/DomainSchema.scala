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
    
    val ChatChannel = ChatChannelClass
    val ChatChannelMember = ChatChannelMemberClass
    val ChatChannelEvent = ChatChannelEventClass
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
    val ChatChannelId = "chatChannelIdSeq"
    val SessionSeq = "SESSIONSEQ"
  }
}