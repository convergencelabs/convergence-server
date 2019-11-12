/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.schema

object ChatTopicChangedEventClass extends OrientDbClass {
  val ClassName = "ChatTopicChangedEvent"
  
  object Fields {
    val Chat = "chat"
    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"
    
    val Topic = "topic"
  }
}