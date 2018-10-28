package com.convergencelabs.server.datastore.domain.schema

object ChatChannelClass extends OrientDBClass {
  val ClassName = "ChatChannel"

  object Indices {
    val Id = "ChatChannel.id"
  }
}
