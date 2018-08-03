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
  }

  object Sequences {
    val AnonymousUsername = "anonymousUsernameSeq"
    val ChatChannelId = "chatChannelIdSeq"
    val SessionSeq = "SESSIONSEQ"
  }
}