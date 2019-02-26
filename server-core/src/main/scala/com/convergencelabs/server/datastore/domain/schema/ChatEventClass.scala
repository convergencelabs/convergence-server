package com.convergencelabs.server.datastore.domain.schema

object ChatEventClass extends OrientDbClass {
  val ClassName = "ChatEvent"

  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"
  }
  
  object Indices {
    val ChatEventNo = "ChatEvent.chat_eventNo"
    val Chat = "ChatEvent.chat"
  }
}