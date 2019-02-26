package com.convergencelabs.server.datastore.domain.schema

object ChatTopicChangedEventClass extends OrientDbClass {
  val ClassName = "ChatTopicChangedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "Timestamp"
    
    val Topic = "topic"
  }
}