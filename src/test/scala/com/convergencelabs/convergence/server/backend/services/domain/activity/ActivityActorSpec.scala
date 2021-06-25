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
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor._
import com.convergencelabs.convergence.server.api.realtime.{ActivityClientActor, ErrorCodes}
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.ActivityPermissionTarget
import com.convergencelabs.convergence.server.backend.services.domain.activity
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.{apply => _, _}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.SetPermissions
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.ActivityId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.{MockDomainPersistenceManager, MockDomainPersistenceProvider}
import org.json4s.JsonAST.{JString, JValue}
import org.mockito.Matchers._
import org.mockito.{Matchers, Mockito}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}


class ActivityActorSpec
  extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val maxWaitTime = FiniteDuration(5, TimeUnit.SECONDS)
  private val noMessageTimeout = FiniteDuration(2, TimeUnit.SECONDS)

  "A ActivityActor" when {
    "a session requests to join" must {
      "successfully auto create on the first join" in new TestFixture {
        val joined: Joined = mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).toOption.get
        joined.ephemeral shouldBe autoCreateOptions.ephemeral
      }

      "allow a convergence user to join / lurk regardless of permissions" in new TestFixture {
        mockJoin(convergenceClient, lurk = true, Map(), Some(autoCreateOptions), Set()).isRight shouldBe true
      }

      "notify joined clients when a new session joins" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions))
        val state = Map("key" -> JString("value"))
        mockJoin(user1Client2, state = state)

        val message: ActivitySessionJoined = user1Client1.clientActor.expectMessageType[ActivitySessionJoined](maxWaitTime)
        message.activityId shouldBe activityId
        message.sessionId shouldBe user1Client2.session.sessionId
        message.state shouldBe state
      }

      "Not notify joined clients when a new session joins in lurk mode" in new TestFixture {
        mockJoin(user1Client1, autoCreateOptions = Some(autoCreateOptions))
        mockJoin(user1Client2, lurk = true)

        user1Client1.clientActor.expectNoMessage(noMessageTimeout)
      }

      "return a NotFoundError if no auto create options are set" in new TestFixture {
        val response: Either[ActivityActor.JoinError, Joined] = mockJoin(user1Client1)
        response shouldBe Left(NotFoundError())
      }

      "return an UnauthorizedError if the user does not have join permissions" in new TestFixture {
        val response: Either[ActivityActor.JoinError, Joined] =
          mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions), Set())
        response.isLeft shouldBe true
        response.left.map(error => {
          error shouldBe an[UnauthorizedError]
        })
      }

      "return an UnauthorizedError if the user does not have lurk permissions" in new TestFixture {
        val response: Either[ActivityActor.JoinError, Joined] =
          mockJoin(user1Client1, lurk = true, Map(), Some(autoCreateOptions), Set(ActivityPermissions.Join))
        response.isLeft shouldBe true
        response.left.map(error => {
          error shouldBe an[UnauthorizedError]
        })
      }

      "return an AlreadyJoinedError if the session is already joined" in new TestFixture {
        mockJoin(client = user1Client1, autoCreateOptions = Some(autoCreateOptions)).isRight shouldBe true
        val response: Either[ActivityActor.JoinError, Joined] = mockJoin(user1Client1)
        response.isLeft shouldBe true
        response.left.map(error => {
          error shouldBe an[AlreadyJoinedError]
        })
      }
    }

    "a session requests to leave" must {
      "respond with Ok if the session leaves" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockLeave(user1Client1) shouldBe Right(Ok())
      }

      "respond with a NotJoinedError if the session is not joined" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockLeave(user1Client2) shouldBe Left(NotJoinedError())
      }

      "Notify other clients that a session has left" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockJoin(user1Client2).isRight shouldBe true
        user1Client1.clientActor.expectMessageType[ActivityClientActor.ActivitySessionJoined](maxWaitTime)
        mockLeave(user1Client2).isRight shouldBe true

        val leaveMessage: ActivitySessionLeft = user1Client1.clientActor.expectMessageType[ActivitySessionLeft](maxWaitTime)
        leaveMessage.activityId shouldBe activityId
        leaveMessage.sessionId shouldBe user1Client2.session.sessionId
      }
    }

    "updating state" must {
      "Send an error message to a client trying to set state on an un joined activity" in new TestFixture {
        activityActor ! ActivityActor.UpdateState(
          domainId, activityId, user1Client1.clientActor.ref, user1Client1.session.sessionId, Map(), complete = false, Set())

        val message: ActivityErrorMessage =
          user1Client1.clientActor.expectMessageType[ActivityClientActor.ActivityErrorMessage](maxWaitTime)

        message.activityId shouldBe activityId
        message.code shouldBe ErrorCodes.ActivityNotJoined
      }

      "Notify other clients that a session update it state" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockJoin(user1Client2).isRight shouldBe true
        user1Client1.clientActor.expectMessageType[ActivityClientActor.ActivitySessionJoined](maxWaitTime)

        val setState = Map("key" -> JString("value"))
        val complete = true
        val removedState = Set("removed")

        activityActor ! ActivityActor.UpdateState(
          domainId, activityId, user1Client2.clientActor.ref, user1Client2.session.sessionId, setState, complete, removedState)

        val message: ActivityStateUpdated = user1Client1.clientActor.expectMessageType[ActivityStateUpdated](maxWaitTime)
        message.activityId shouldBe activityId
        message.sessionId shouldBe user1Client2.session.sessionId
        message.set shouldBe setState
        message.complete shouldBe complete
        message.removed shouldBe removedState
      }

      "Not notify other clients that a session update it state after they leave" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockJoin(user1Client2).isRight shouldBe true
        user1Client1.clientActor.expectMessageType[ActivityClientActor.ActivitySessionJoined](maxWaitTime)

        mockLeave(user1Client1).isRight shouldBe true

        activityActor ! ActivityActor.UpdateState(
          domainId, activityId, user1Client2.clientActor.ref, user1Client2.session.sessionId, Map(), complete = false, Set())

        user1Client1.clientActor.expectNoMessage(noMessageTimeout)
      }

      "properly update partial state" in new TestFixture {
        val initialState = Map("k1" -> JString("v1"), "k2" -> JString("v2"), "k3" -> JString("v3"))
        mockJoin(user1Client1, lurk = false, initialState, Some(autoCreateOptions)).isRight shouldBe true

        val setState = Map("k1" -> JString("v1-new"), "k4" -> JString("v4"))
        val removeState = Set("k2")
        activityActor ! ActivityActor.UpdateState(
          domainId, activityId, user1Client1.clientActor.ref, user1Client1.session.sessionId, setState, complete = false, removeState)

        val response: Either[JoinError, Joined] = mockJoin(user1Client2)
        response.isRight shouldBe true
        response.map { joined =>
          val currentState = joined.state(user1Client1.session.sessionId)
          currentState shouldBe Map(
            "k1" -> JString("v1-new"),
            "k3" -> JString("v3"),
            "k4" -> JString("v4"))
        }
      }

      "properly update complete state" in new TestFixture {
        val initialState = Map("k1" -> JString("v1"), "k2" -> JString("v2"), "k3" -> JString("v3"))
        mockJoin(user1Client1, lurk = false, initialState, Some(autoCreateOptions)).isRight shouldBe true

        val setState = Map("k1" -> JString("v1-new"), "k4" -> JString("v4"))
        val removeState = Set("k1")
        activityActor ! ActivityActor.UpdateState(
          domainId, activityId, user1Client1.clientActor.ref, user1Client1.session.sessionId, setState, complete = true, removeState)

        val response: Either[JoinError, Joined] = mockJoin(user1Client2)
        response.isRight shouldBe true
        response.map { joined =>
          val currentState = joined.state(user1Client1.session.sessionId)
          currentState shouldBe Map(
            "k1" -> JString("v1-new"),
            "k4" -> JString("v4"))
        }
      }
    }

    "deleting and activity" must {
      "return Ok if the activity exists and no requester is set" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockDelete(None).response shouldBe Right(Ok())
      }

      "return Ok if the activity exists and the requester is a convergence user" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true
        mockDelete(Some(convergenceClient.session)).response shouldBe Right(Ok())
      }

      "return an UnauthorizedError if the requester does not have remove permissions" in new TestFixture {
        val response: DeleteResponse = mockDelete(Some(user2Client1.session))
        response.response.isLeft shouldBe true
        response.response.left.map { err =>
          err shouldBe an[UnauthorizedError]
        }
      }

      "return a ActivityNotFound if the activity does not exist" in new TestFixture {
        Mockito.when(persistenceProvider.activityStore.deleteActivity(activityId))
          .thenReturn(Failure(EntityNotFoundException()))
        mockDelete(None).response shouldBe Left(NotFoundError())
      }

      "notify connected clients that the activity was deleted" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true

        mockDelete(None).response shouldBe Right(Ok())

        val message: ActivityDeleted = user1Client1.clientActor.expectMessageType[ActivityDeleted](maxWaitTime)
        message.activityId shouldBe activityId
      }
    }

    "setting permissions" must {
      "respond with Unauthorized if the user doesn't have permissions" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions), Set(ActivityPermissions.Join)).isRight shouldBe true

        val sp: SetPermissions = SetPermissions(None, None, None)
        val replyTo: TestProbe[SetPermissionsResponse] = createTestProbe[SetPermissionsResponse]()
        activityActor ! SetPermissionsRequest(domainId, activityId, Some(user1Client1.session), sp, replyTo.ref)
        val response: SetPermissionsResponse = replyTo.expectMessageType[SetPermissionsResponse](maxWaitTime)
        response.response.isLeft shouldBe true
        response.response.left.map { err =>
          err shouldBe an[UnauthorizedError]
        }
      }

      "send a force leave to any client that loses permissions" in new TestFixture {
        mockJoin(user1Client1, lurk = false, Map(), Some(autoCreateOptions)).isRight shouldBe true

        val sp: SetPermissions = SetPermissions(None, None, None)
        val replyTo: TestProbe[SetPermissionsResponse] = createTestProbe[SetPermissionsResponse]()
        activityActor ! SetPermissionsRequest(domainId, activityId, None, sp, replyTo.ref)

        Mockito.when(persistenceProvider.permissionsStore
          .resolveUserPermissionsForTarget(user1Client1.session.userId, ActivityPermissionTarget(activityId), ActivityPermissions.AllActivityPermissions))
          .thenReturn(Success(Set()))

        val response: SetPermissionsResponse = replyTo.expectMessageType[SetPermissionsResponse](maxWaitTime)
        response.response.isRight shouldBe true

        val leave: ActivityForceLeave = user1Client1.clientActor.expectMessageType[ActivityForceLeave](maxWaitTime)
        leave.activityId shouldBe activityId
      }
    }
  }

  private trait TestFixture {
    val domainId: DomainId = DomainId("convergence", "default")
    val activityId: ActivityId = ActivityId("type", "id")

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
    Mockito.when(persistenceProvider.activityStore.deleteActivity(activityId)).thenReturn(Success(()))

    Mockito.when(persistenceProvider.permissionsStore
      .setPermissionsForTarget(any(), any(), any(), any(), any(), any())).thenReturn(Success(()))
    Mockito.when(persistenceProvider.permissionsStore
      .resolveUserPermissionsForTarget(any(), Matchers.eq(ActivityPermissionTarget(activityId)), Matchers.eq(ActivityPermissions.AllActivityPermissions)))
      .thenReturn(Success(Set()))
    Mockito.when(persistenceProvider.permissionsStore
      .removeAllPermissionsForTarget(ActivityPermissionTarget(activityId))).thenReturn(Success(()))
    Mockito.when(
      this.persistenceProvider.permissionsStore.setPermissionsForTarget(any(), any(), any(), any(), any(), any()))
      .thenReturn(Success(()))

    val shardRegion: TestProbe[ActivityActor.Message] = testKit.createTestProbe[ActivityActor.Message]()
    val shard: TestProbe[ClusterSharding.ShardCommand] = testKit.createTestProbe[ClusterSharding.ShardCommand]()
    val receiveTimeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
    private val behavior = ActivityActor(domainId, activityId, shardRegion.ref, shard.ref, persistenceManager, receiveTimeout)

    val activityActor: ActorRef[activity.ActivityActor.Message] = testKit.spawn(behavior)

    def mockJoin(client: ActivityClient,
                 lurk: Boolean = false,
                 state: Map[String, JValue] = Map(),
                 autoCreateOptions: Option[ActivityAutoCreationOptions] = None,
                 permissions: Set[String] = ActivityPermissions.AllActivityPermissions): Either[ActivityActor.JoinError, Joined] = {

      Mockito.when(persistenceProvider.permissionsStore
        .resolveUserPermissionsForTarget(client.session.userId, ActivityPermissionTarget(activityId), ActivityPermissions.AllActivityPermissions))
        .thenReturn(Success(permissions))

      val replyTo = testKit.createTestProbe[ActivityActor.JoinResponse]()

      activityActor !
        ActivityActor.JoinRequest(domainId, activityId, client.session, lurk, state, autoCreateOptions, client.clientActor.ref, replyTo.ref)
      val response: ActivityActor.JoinResponse =
        replyTo.expectMessageType[ActivityActor.JoinResponse](FiniteDuration(20, TimeUnit.SECONDS))
      response.response
    }

    def mockLeave(client: ActivityClient): Either[LeaveError, Ok] = {
      val replyTo = testKit.createTestProbe[ActivityActor.LeaveResponse]()

      activityActor !
        ActivityActor.LeaveRequest(domainId, activityId, client.session.sessionId, replyTo.ref)

      val response: ActivityActor.LeaveResponse =
        replyTo.expectMessageType[ActivityActor.LeaveResponse](FiniteDuration(20, TimeUnit.SECONDS))

      response.response
    }

    def mockDelete(requester: Option[DomainSessionAndUserId]): DeleteResponse = {
      val replyTo: TestProbe[DeleteResponse] = createTestProbe[DeleteResponse]()
      activityActor ! ActivityActor.DeleteRequest(domainId, activityId, requester, replyTo.ref)

      val response: DeleteResponse = replyTo.expectMessageType[DeleteResponse](maxWaitTime)
      response
    }
  }

  case class ActivityClient(clientActor: TestProbe[ActivityClientActor.OutgoingMessage],
                            session: DomainSessionAndUserId)
}
