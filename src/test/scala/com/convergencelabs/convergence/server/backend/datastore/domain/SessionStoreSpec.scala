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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.backend.datastore.domain.session.SessionQueryType
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.DeltaCategory
import com.convergencelabs.convergence.server.model.domain.session
import com.convergencelabs.convergence.server.model.domain.session.{DomainSession, DomainSessionAndUserId}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import com.convergencelabs.convergence.server.model.{DomainId, domain}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.OptionValues
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// scalastyle:off magic.number multiple.string.literals
class SessionStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with AnyWordSpecLike
    with OptionValues
    with Matchers {

  private val userId = DomainUserId.normal("test")
  private val user = DomainUser(userId, None, None, None, None, None)

  private val sessionId = "u1-1"
  private val authMethod = "jwt"
  private val client = "javascript"
  private val clientVersion = "1.0"
  private val remoteHost = "127.0.0.1"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProviderImpl(DomainId("ns", "domain"), dbProvider)

  "A SessionStore" when {
    "creating a session" must {
      "succeed when creating a session with no disconnected time" in withTestData { provider =>
        val session = DomainSession(sessionId, userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session
      }

      "succeed when creating a session with at disconnected time" in withTestData { provider =>
        val session = domain.session.DomainSession(sessionId, userId, truncatedInstantNow(), Some(truncatedInstantNow()), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session
      }

      "disallow duplicate session Ids" in withTestData { provider =>
        val session = domain.session.DomainSession(sessionId, userId, truncatedInstantNow(), Some(truncatedInstantNow()), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        provider.sessionStore.createSession(session).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "marking a session as disconnected" must {
      "succeed when disconnecting a connected session" in withTestData { provider =>
        val session = domain.session.DomainSession(sessionId, userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val disconnected = truncatedInstantNow()
        provider.sessionStore.setSessionDisconnected(sessionId, disconnected).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session.copy(disconnected = Some(disconnected))
      }

      "fail when disconnecting an already disconnected session" in withTestData { provider =>
        val originalTime = truncatedInstantNow()
        val newTime = originalTime.plusSeconds(5)
        val session = domain.session.DomainSession(sessionId, userId, truncatedInstantNow(), Some(originalTime), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        provider.sessionStore.getSession(sessionId).get.value shouldBe session
        provider.sessionStore.setSessionDisconnected(sessionId, newTime).failure
      }
    }

    "getting connected sessions" must {
      "only return sessions that are connected" in withTestData { provider =>
        val session1 = session.DomainSession("1", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = session.DomainSession("2", userId, truncatedInstantNow(), Some(truncatedInstantNow()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = session.DomainSession("3", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val connected = provider.sessionStore.getSessions(
          None,
          None,
          None,
          None,
          excludeDisconnected = true,
          SessionQueryType.All,
          QueryOffset(),
          QueryLimit()).get

        connected.data.toSet shouldBe Set(session1, session3)
      }
    }
    
    "getting connected sessions count" must {
      "only count sessions that are connected" in withTestData { provider =>
        val session1 = session.DomainSession("1", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = session.DomainSession("2", userId, truncatedInstantNow(), Some(truncatedInstantNow()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = session.DomainSession("3", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val connectedCount = provider.sessionStore.getConnectedSessionsCount(SessionQueryType.All).get
        connectedCount.shouldBe(2)
      }
      
      "only count non admin sessions that are connected when SessionQueryType.NonAdmin is used" in withTestData { provider =>
        val session1 = session.DomainSession("1", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = session.DomainSession("2", userId, truncatedInstantNow(), Some(truncatedInstantNow()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = session.DomainSession("3", userId, truncatedInstantNow(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val connectedCount = provider.sessionStore.getConnectedSessionsCount(SessionQueryType.ExcludeConvergence).get
        connectedCount.shouldBe(2)
      }
    }
    
    "nextSessionId" must {
      "return unique values" in withTestData { provider =>
        val session1 = provider.sessionStore.nextSessionId.get
        val session2 = provider.sessionStore.nextSessionId.get
        session1 shouldNot equal(session2)
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createDomainUser(user)
      testCode(provider)
    }
  }
}
