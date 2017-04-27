package com.convergencelabs.server.domain

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

object ChatMessageBroadcaster {
  def getChatUsernameTopicName(username: String): String = {
    return s"chat-user-${username}"
  }
}

class ChatMessageBroadcaster(mediator: ActorRef) {
  private[this] def broadcastToChannel(channelId: String, message: AnyRef, members: List[String]) {
    members.foreach { member =>
      val topic = ChatMessageBroadcaster.getChatUsernameTopicName(member)
      mediator ! Publish(topic, message)
    }
  }
}