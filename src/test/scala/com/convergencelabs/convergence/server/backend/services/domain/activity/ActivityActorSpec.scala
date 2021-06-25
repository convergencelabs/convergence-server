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

package com.convergencelabs.convergence.server.backend.services.domain.activity

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.ActivityPermissionTarget
import com.convergencelabs.convergence.server.backend.services.domain.activity
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.{Joined, LeaveError, NotFoundError, NotJoinedError}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.ActivityId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import org.json4s.JsonAST.JValue
import org.mockito.Matchers._
import org.mockito.{Matchers, Mockito}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.Success


class ActivityActorSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A ActivityActor" when {
    "a session requests to join" must {
      "return not found if not auto create options are set" in new TestFixture {
        val response: Either[ActivityActor.JoinError, Joined] = mockJoin(user1Client1)
        response shouldBe Left(NotFoundError())
      }

      "successfully auto create on the first join" in new TestFixture {
        val joined: Joined = mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).toOption.get
        joined.ephemeral shouldBe autoCreateOptions.ephemeral
      }

      "Notify joined clients when a new session joins auto create on the first join" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions))
        mockJoin(user1Client2)

        user1Client1.clientActor.expectMessageType[ActivityClientActor.ActivitySessionJoined](FiniteDuration(20, TimeUnit.SECONDS))
      }
    }

    "a session requests to leave" must {
      "respond with Ok if the session leaves" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions))
        mockLeave(user1Client1) shouldBe Right(Ok())
      }

      "respond with a NotJoinedError if the session is not joined" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions))
        mockLeave(user1Client2) shouldBe Left(NotJoinedError())
      }
    }
  }


  private trait TestFixture {
    val domainId: DomainId = DomainId("convergence", "default")
    val activityId: ActivityId = ActivityId("type", "id")
    val now: Instant = Instant.now()

    val userId1: DomainUserId = DomainUserId.normal("user1")
    val userId2: DomainUserId = DomainUserId.normal("user2")

    val convergenceUser: DomainUserId = DomainUserId.convergence("admin")

    val user1Client1: ActivityClient = ActivityClient(
      testKit.createTestProbe[ActivityClientActor.OutgoingMessage](),
      DomainSessionAndUserId("s1", userId1)
    )

    val user1Client2: ActivityClient = ActivityClient(
      testKit.createTestProbe[ActivityClientActor.OutgoingMessage](),
      DomainSessionAndUserId("s2", userId1)
    )

    val user2Client1: ActivityClient = ActivityClient(
      testKit.createTestProbe[ActivityClientActor.OutgoingMessage](),
      DomainSessionAndUserId("s3", userId2)
    )

    val convergenceClient: ActivityClient = ActivityClient(
      testKit.createTestProbe[ActivityClientActor.OutgoingMessage](),
      DomainSessionAndUserId("s4", convergenceUser)
    )

    val autoCreateOptions: ActivityAutoCreationOptions = ActivityAutoCreationOptions(ephemeral = false, Set(), Map(), Map())
    val persistenceProvider = new MockDomainPersistenceProvider(domainId)
    val persistenceManager = new MockDomainPersistenceManager(Map(domainId -> persistenceProvider))

    Mockito.when(persistenceProvider.activityStore.exists(activityId)).thenReturn(Success(false))
    Mockito.when(persistenceProvider.activityStore.findActivity(activityId)).thenReturn(Success(None))
    Mockito.when(persistenceProvider.activityStore.createActivity(any())).thenReturn(Success(()))

    Mockito.when(persistenceProvider.permissionsStore.setPermissionsForTarget(any(), any(), any(), any(), any(), any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.permissionsStore
      .resolveUserPermissionsForTarget(any(), Matchers.eq(ActivityPermissionTarget(activityId)), Matchers.eq(ActivityPermissions.AllActivityPermissions)))
      .thenReturn(Success(Set()))

    val shardRegion: TestProbe[ActivityActor.Message] = testKit.createTestProbe[ActivityActor.Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val receiveTimeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
    private val behavior = ActivityActor(domainId, activityId, shardRegion.ref, shard.ref, persistenceManager, receiveTimeout)

    val activityActor: ActorRef[activity.ActivityActor.Message] = testKit.spawn(behavior)

    def mockLeave(client: ActivityClient): Either[LeaveError, Ok] = {
      val replyTo = testKit.createTestProbe[ActivityActor.LeaveResponse]()

      activityActor !
        ActivityActor.LeaveRequest(domainId, activityId, client.session.sessionId,replyTo.ref)

      val response: ActivityActor.LeaveResponse =
        replyTo.expectMessageType[ActivityActor.LeaveResponse](FiniteDuration(20, TimeUnit.SECONDS))

      response.response
    }

    def mockJoin(client: ActivityClient,
                 lurk: Boolean = false,
                 state: Map[String, JValue] = Map(),
                 autoCreateOptions: Option[ActivityAutoCreationOptions] = None
                ): Either[ActivityActor.JoinError, Joined] = {

      Mockito.when(persistenceProvider.permissionsStore
        .resolveUserPermissionsForTarget(client.session.userId, ActivityPermissionTarget(activityId), ActivityPermissions.AllActivityPermissions))
        .thenReturn(Success(ActivityPermissions.AllActivityPermissions))

      val replyTo = testKit.createTestProbe[ActivityActor.JoinResponse]()

      activityActor !
        ActivityActor.JoinRequest(domainId, activityId, client.session, lurk, state, autoCreateOptions, client.clientActor.ref, replyTo.ref)
      val response: ActivityActor.JoinResponse =
        replyTo.expectMessageType[ActivityActor.JoinResponse](FiniteDuration(20, TimeUnit.SECONDS))
      response.response
    }
  }

  case class ActivityClient(clientActor: TestProbe[ActivityClientActor.OutgoingMessage],
                            session: DomainSessionAndUserId)
}
