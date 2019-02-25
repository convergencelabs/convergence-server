package com.convergencelabs.server.api.rest.domain

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.DeleteCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollection
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollectionSummaries
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.GetCollections
import com.convergencelabs.server.datastore.domain.CollectionStoreActor.UpdateCollection
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile
import com.convergencelabs.server.api.rest._

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.api.rest.domain.DomainConfigService.ModelSnapshotPolicyData
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetChats
import com.convergencelabs.server.datastore.domain.ChatChannelInfo

object DomainChatService {
}

class DomainChatService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainCollectionService._
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
    pathPrefix("chats") {
      pathEnd {
        get {
          parameters("filter".?, "offset".as[Int].?, "limit".as[Int].?) { (filter, offset, limit) =>
            complete(getChats(domain, offset, limit))
          }
        } 
      }
    }
  }

  def getChats(domain: DomainFqn, offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetChats(offset, limit))
    println(message)
    (domainRestActor ? message).mapTo[List[ChatChannelInfo]] map { chats =>
      okResponse(chats)
    }
  }
}
