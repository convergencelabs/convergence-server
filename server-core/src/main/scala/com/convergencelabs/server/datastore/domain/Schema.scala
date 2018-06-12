package com.convergencelabs.server.datastore.domain

object Schema {
  /////////////////////////////////////////////////////////////////////////////
  // Chat Classes
  /////////////////////////////////////////////////////////////////////////////

  object Sequences {
    val ChatChannelId = "chatChannelIdSeq"
  }

  object Classes {

    object User {
      val Class = "User"
      object Indices {
        val Username = "User.username"
        val Email = "User.email"
      }
    }

    object UserReconnectToken {
      val Class = "UserReconnectToken"

      object Indices {
        val Token = "UserReconnectToken.token"
      }
    }

    object Collection {
      val Class = "Collection"

      object Indices {
        val Id = "Collection.id"
      }
    }

    object ChatChannel {
      val Class = "ChatChannel"

      object Indices {
        val Id = "ChatChannel.id"
      }
    }

    object ChatChannelEvent {
      val Class = "ChatChannelEvent"

      object Indices {
        val Channel_EventNo = "ChatChannelEvent.channel_eventNo"
        val Channel = "ChatChannelEvent.channel"
      }
    }

    object ChatCreatedEvent {
      val Class = "ChatCreatedEvent"
    }

    object ChatMessageEvent {
      val Class = "ChatMessageEvent"
    }

    object ChatUserJoinedEvent {
      val Class = "ChatUserJoinedEvent"
    }

    object ChatUserLeftEvent {
      val Class = "ChatUserLeftEvent"
    }

    object ChatUserAddedEvent {
      val Class = "ChatUserAddedEvent"
    }

    object ChatUserRemovedEvent {
      val Class = "ChatUserRemovedEvent"
    }

    object ChatNameChangedEvent {
      val Class = "ChatNameChangedEvent"
    }

    object ChatTopicChangedEvent {
      val Class = "ChatTopicChangedEvent"
    }

    object ChatChannelMember {
      val Class = "ChatChannelMember"

      object Indices {
        val Channel_User = "ChatChannelMember_channel_user"
        val Channel = "ChatChannelMember_channel"
      }
    }
  }
}