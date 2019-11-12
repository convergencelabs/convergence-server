/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ChatMemberClass extends OrientDbClass {
  val ClassName = "ChatMember"
  
  object Fields {
    val Chat = "chat"
    val User = "user"
    val Seen = "seen"
  }

  object Indices {
    val Chat_User = "ChatMember.chat_user"
    val Chat = "ChatMember.chat"
  }
}
