package com.convergencelabs.server.domain.presence

object PresenceSubscriptionUtil {
  def userPresencePubSubTopic(username: String): String = {
    s"presence::${username}"
  }
}

