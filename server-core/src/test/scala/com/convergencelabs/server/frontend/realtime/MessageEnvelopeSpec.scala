package com.convergencelabs.server.frontend.realtime

import scala.language.postfixOps
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL.int2jvalue
import org.json4s.JsonDSL.jobject2assoc
import org.json4s.JsonDSL.pair2Assoc
import org.json4s.JsonDSL.pair2jvalue
import org.json4s.JsonDSL.string2jvalue
import org.json4s.JsonDSL.boolean2jvalue
import org.json4s.jackson.JsonMethods.compact
import org.json4s.jackson.JsonMethods.render
import org.junit.runner.RunWith
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import com.convergencelabs.server.domain.model.data.ObjectValue

class MessageEnvelopeSpec
    extends WordSpecLike
    with Matchers {

  "A MessageEnvelop" when {

    "creating a message envelop from JSON" must {

      "return a failure when applying from invalid JSON" in {
        MessageEnvelope("{}").failure
      }

      "return a success when applying from valid JSON" in {
        val expected = MessageEnvelope(new AutoCreateModelConfigResponseMessage("collectionId", Some(ObjectValue("id", Map())), None, None, None), None, Some(1L))
        val json = MessageSerializer.writeJson(expected)
        MessageEnvelope(json).success.value shouldBe expected
      }
    }
  }
}
