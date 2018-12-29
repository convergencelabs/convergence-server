package com.convergencelabs.server.datastore.domain.schema

object ChatChannelMemberClass extends OrientDBClass {
  val ClassName = "ChatChannelMember"

  object Indices {
    val Channel_User = "ChatChannelMember.channel_user"
    val Channel = "ChatChannelMember.channel"
  }
}
