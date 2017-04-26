package com.convergencelabs.server.domain

import java.time.Instant

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

object ChatChannelLookupActor {

  val RelativePath = "chatChannelLookupActor"

  def props(domainFqn: DomainFqn): Props = Props(
    new ChatChannelLookupActor(domainFqn))

  case class GetChannelsRequest(username: String)
  case class GetJoinedChannelsRequest(username: String)
  case class GetDirectChannelsRequest(username: String, userLists: List[List[String]])
}

class ChatChannelLookupActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  import ChatChannelLookupActor._

  val mediator = DistributedPubSub(context.system).mediator

  def receive: Receive = {
    case message: GetChannelsRequest =>
      onGetChannels(message)
    case message: GetJoinedChannelsRequest =>
      onGetJoinedChannels(message)
    case message: GetDirectChannelsRequest =>
      onGetDirect(message)
  }

  def onGetChannels(message: GetChannelsRequest): Unit = {
    val GetChannelsRequest(username) = message
    ???
  }

  def onGetDirect(message: GetDirectChannelsRequest): Unit = {
    val GetDirectChannelsRequest(username, usernameLists) = message;
    ???
  }

  def onGetJoinedChannels(message: GetJoinedChannelsRequest): Unit = {
    val GetJoinedChannelsRequest(username) = message
    ???
  }
}
