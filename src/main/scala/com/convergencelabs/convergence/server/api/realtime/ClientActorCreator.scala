/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.http.scaladsl.model.RemoteAddress
import com.convergencelabs.convergence.server.backend.services.domain.DomainActor
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatDeliveryActor, ChatServiceActor}
import com.convergencelabs.convergence.server.backend.services.domain.identity.IdentityServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationServiceActor, ModelServiceActor, RealtimeModelActor}
import com.convergencelabs.convergence.server.backend.services.domain.presence.PresenceServiceActor
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId

import scala.concurrent.duration.Duration

/**
 * A helper actor that will spawn and supervise ClientActors. A reference to
 * an instance of this actor will be sent to the WebSocketService and used
 * to create ClientActors for incoming WebSocket connections.
 */
private[server] object ClientActorCreator {

  /**
   * Creates the ClientActorCreator actor.
   *
   * @param protocolConfig          The configuration to use for the WebSocket
   *                                protocol
   * @param domainRegion            The shard region for DomainActors.
   * @param activityShardRegion     The shard region for ActivityActors.
   * @param modelShardRegion        The shard region for RealtimeModelActors.
   * @param chatShardRegion         The shard region for ChatActors.
   * @param chatDeliveryShardRegion The shard region for actors that deliver
   *                                chat messages to clients.
   * @param domainLifecycleTopic    The domain lifecycle pub-sub topic.
   * @return An ActorRef for the created behavior.
   */
  def apply(protocolConfig: ProtocolConfiguration,
            domainRegion: ActorRef[DomainActor.Message],
            modelService: ActorRef[ModelServiceActor.Message],
            modelOperationService: ActorRef[ModelOperationServiceActor.Message],
            chatService: ActorRef[ChatServiceActor.Message],
            identityService: ActorRef[IdentityServiceActor.Message],
            presenceService: ActorRef[PresenceServiceActor.Message],
            activityShardRegion: ActorRef[ActivityActor.Message],
            modelShardRegion: ActorRef[RealtimeModelActor.Message],
            chatShardRegion: ActorRef[ChatActor.Message],
            chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
            domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage]): Behavior[CreateClientRequest] = Behaviors.setup { context =>
    val modelSyncInterval = Duration.fromNanos(
      context.system.settings.config.getDuration("convergence.offline.model-sync-interval").toNanos)

    val receive = Behaviors.receiveMessage[CreateClientRequest] {
      case CreateClientRequest(domainId, remoteAddress, userAgent, replyTo) =>
        val clientActor = context.spawnAnonymous(ClientActor(
          domainId,
          protocolConfig,
          remoteAddress,
          userAgent,
          domainRegion,
          modelService,
          modelOperationService,
          chatService,
          identityService,
          presenceService,
          activityShardRegion,
          modelShardRegion,
          chatShardRegion,
          chatDeliveryShardRegion,
          domainLifecycleTopic,
          modelSyncInterval)
        )

        val response = CreateClientResponse(clientActor)

        replyTo ! response

        Behaviors.same
    }

    Behaviors.supervise(receive).onFailure(SupervisorStrategy.restart)
  }


  /**
   * Requests that a ClientActor should be spawned, and that a reference
   * to that actor be returned.
   *
   * @param domain     The domain the client is connecting to.
   * @param remoteHost The remote host the client is connecting from.
   * @param userAgent  The HTTP User Agent, if set.
   * @param replyTo    The actor to reply to.
   */
  private[realtime] final case class CreateClientRequest(domain: DomainId,
                                                         remoteHost: RemoteAddress,
                                                         userAgent: String,
                                                         replyTo: ActorRef[CreateClientResponse])

  /**
   * Returns the ActorRef of the created client to the requester.
   *
   * @param client The actor ref of the ClientActor.
   */
  private[realtime] final case class CreateClientResponse(client: ActorRef[ClientActor.WebSocketMessage])

}


