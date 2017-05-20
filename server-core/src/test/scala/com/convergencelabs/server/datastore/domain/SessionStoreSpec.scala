package com.convergencelabs.server.datastore.domain

import java.time.Instant

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.domain.SessionStore.SessionQueryType
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType

// scalastyle:off magic.number multiple.string.literals
class SessionStoreSpec
    extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
    with WordSpecLike
    with OptionValues
    with Matchers {

  val username = "test"
  val user = DomainUser(DomainUserType.Normal, username, None, None, None, None)

  val sessionId = "u1-1"
  val authMethod = "jwt"
  val client = "javascript"
  val clientVersion = "1.0"
  val remoteHost = "127.0.0.1"

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProvider(dbProvider)

  "A SessionStore" when {
    "creating a session" must {
      "succeed when creating a session with no disconnected time" in withTestData { provider =>
        val session = DomainSession(sessionId, username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session
      }

      "succeed when creating a session with at disconnected time" in withTestData { provider =>
        val session = DomainSession(sessionId, username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session
      }

      "disallow duplicate session Ids" in withTestData { provider =>
        val session = DomainSession(sessionId, username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        provider.sessionStore.createSession(session).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "marking a session as disconnected" must {
      "succeed when disconnecting a connected session" in withTestData { provider =>
        val session = DomainSession(sessionId, username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        val disconneced = Instant.now()
        provider.sessionStore.setSessionDisconneted(sessionId, disconneced).get
        val queried = provider.sessionStore.getSession(sessionId).get.value
        queried shouldBe session.copy(disconnected = Some(disconneced))
      }

      "fail when disconnecting an already disconnected session" in withTestData { provider =>
        val session = DomainSession(sessionId, username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        provider.sessionStore.createSession(session).get
        provider.sessionStore.getSession(sessionId).get.value shouldBe session
        provider.sessionStore.setSessionDisconneted(sessionId, Instant.now()).failure
      }
    }

    "getting connected sessions" must {
      "only return sessions that are connected" in withTestData { provider =>
        val session1 = DomainSession("1", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = DomainSession("2", username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = DomainSession("3", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val conneted = provider.sessionStore.getSessions(
          None,
          None,
          None,
          None,
          true,
          SessionQueryType.All,
          None,
          None).get

        conneted.toSet shouldBe Set(session1, session3)
      }
    }
    
    "getting connected sessions count" must {
      "only count sessions that are connected" in withTestData { provider =>
        val session1 = DomainSession("1", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = DomainSession("2", username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = DomainSession("3", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val connetedCount = provider.sessionStore.getConnectedSessionsCount(SessionQueryType.All).get
        connetedCount.shouldBe(2)
      }
      
      "only count non admin sessions that are connected when SessionQueryType.NonAdmin is used" in withTestData { provider =>
        val session1 = DomainSession("1", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)
        val session2 = DomainSession("2", username, Instant.now(), Some(Instant.now()), authMethod, client, clientVersion, "", remoteHost)
        val session3 = DomainSession("3", username, Instant.now(), None, authMethod, client, clientVersion, "", remoteHost)

        provider.sessionStore.createSession(session1).get
        provider.sessionStore.createSession(session2).get
        provider.sessionStore.createSession(session3).get

        val connetedCount = provider.sessionStore.getConnectedSessionsCount(SessionQueryType.NonAdmin).get
        connetedCount.shouldBe(2)
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
