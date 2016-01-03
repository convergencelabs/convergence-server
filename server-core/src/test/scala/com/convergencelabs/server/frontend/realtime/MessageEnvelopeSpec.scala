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

class MessageEnvelopeSpec
    extends WordSpecLike
    with Matchers {

  "A MessageEnvelop" when {

    "creating a message envelop from JSON" must {

      "return a failure when applying from invalid JSON" in {
        MessageEnvelope("{}").failure
      }

      "return a success when applying from valid JSON" in {

        val jValue = ("opCode" -> "rqst") ~
          ("reqId" -> 1) ~
          ("type" -> "handshake") ~
          ("body" ->
            ("reconnect" -> false))

        val json = compact(render(jValue))

        MessageEnvelope(json).success.value shouldBe MessageEnvelope(
          OpCode.Request, Some(1L), Some(MessageType.Handshake), Some(JObject("reconnect" -> JBool.False)))
      }
    }

    "creating from an outgoing protocol message" must {

      "correctly greate a respose message" in {
        val response = HandshakeResponseMessage(true, None, None, None)
        val asJosn = MessageSerializer.decomposeBody(Some(response))
        MessageEnvelope(1L, response) shouldBe
          MessageEnvelope(OpCode.Reply, Some(1L), None, asJosn)

      }

      "correctly greate a normal message" in {
        val normal = OperationAcknowledgementMessage("foo", 1L, 2L)
        val asJosn = MessageSerializer.decomposeBody(Some(normal))
        MessageEnvelope(normal) shouldBe
          MessageEnvelope(OpCode.Normal, None, Some(MessageType.OperationAck), asJosn)
      }

      "correctly greate a request message" in {
        val request = ModelDataRequestMessage(ModelFqnData("foo", "bar"))
        val asJosn = MessageSerializer.decomposeBody(Some(request))
        MessageEnvelope(1L, request) shouldBe
          MessageEnvelope(OpCode.Request, Some(1L), Some(MessageType.ModelDataRequest), asJosn)
      }
    }
  }
}
