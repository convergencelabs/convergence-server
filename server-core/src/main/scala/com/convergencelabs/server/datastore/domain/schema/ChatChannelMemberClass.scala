package com.convergencelabs.server.datastore.domain.schema

object ChatChannelMemberClass extends OrientDbClass {
  val ClassName = "ChatChannelMember"

  object Indices {
    val Channel_User = "ChatChannelMember.channel_user"
    val Channel = "ChatChannelMember.channel"
  }
}
