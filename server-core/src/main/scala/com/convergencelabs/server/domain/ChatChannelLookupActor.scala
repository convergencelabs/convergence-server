package com.convergencelabs.server.domain

import java.time.Instant

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.convergencelabs.server.domain.ChatChannelMessages.CreateChannelRequest
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import scala.util.control.NonFatal
import com.convergencelabs.server.datastore.domain.ChatChannelStore.ChannelType
import com.convergencelabs.server.domain.ChatChannelMessages.CreateChannelResponse
import akka.actor.Status
import com.convergencelabs.server.datastore.domain.ChatCreatedEvent

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

  var chatChannelStore: ChatChannelStore = _

  def receive: Receive = {
    case message: CreateChannelRequest =>
      onCreateChannel(message)
    case message: GetChannelsRequest =>
      onGetChannels(message)
    case message: GetJoinedChannelsRequest =>
      onGetJoinedChannels(message)
    case message: GetDirectChannelsRequest =>
      onGetDirect(message)
  }

  def onCreateChannel(message: CreateChannelRequest): Unit = {
    val CreateChannelRequest(channelId, channelType, channelMembership, name, topic, members, createdBy) = message
    ChannelType.withNameOpt(channelType) match {
      case Some(ct) =>
        val isPrivate = channelMembership.toLowerCase match {
          case "private" => true
          case _ => false
        }

        (for {
          id <- this.chatChannelStore.createChatChannel(channelId, ct, isPrivate, name.getOrElse(""), topic.getOrElse(""))
          _ <- this.chatChannelStore.addChatCreatedEvent(ChatCreatedEvent(0, id, createdBy, Instant.now(), name.getOrElse(""), topic.getOrElse(""), members))
        } yield {
          sender ! CreateChannelResponse(id)
        }) recover {
          case NonFatal(cause) =>
            sender ! Status.Failure(cause)
        }
      case None =>
        sender ! Status.Failure(new IllegalArgumentException(s"Invalid channel type: ${channelType}"))
    }
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

  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) map { provider =>
      chatChannelStore = provider.chatChannelStore
      ()
    } recover {
      case NonFatal(cause) =>
        throw cause
    }
  }
}
