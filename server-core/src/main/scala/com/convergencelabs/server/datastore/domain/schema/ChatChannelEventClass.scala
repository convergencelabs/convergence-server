package com.convergencelabs.server.datastore.domain.schema

object ChatChannelEventClass extends OrientDBClass {
  val ClassName = "ChatChannelEvent"

  object Indices {
    val Channel_EventNo = "ChatChannelEvent.channel_eventNo"
    val Channel = "ChatChannelEvent.channel"
  }
}