package com.convergencelabs.server.datastore.domain

import java.time.Instant
import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JNothing
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.jvalue2monadic
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.datastore.DuplicateValueExcpetion

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
        provider.sessionStore.createSession(session).failure.exception shouldBe a[DuplicateValueExcpetion]
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
        provider.sessionStore.setSessionDisconneted(sessionId, Instant.now()).failure.exception.printStackTrace()
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
        
        val conneted = provider.sessionStore.getConnectedSessions().get
        conneted.toSet shouldBe Set(session1, session3)
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
