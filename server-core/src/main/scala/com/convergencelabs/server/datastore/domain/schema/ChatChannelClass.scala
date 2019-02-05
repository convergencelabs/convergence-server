package com.convergencelabs.server.datastore.domain.schema

object ChatChannelClass extends OrientDbClass {
  val ClassName = "ChatChannel"

  object Indices {
    val Id = "ChatChannel.id"
  }
}
