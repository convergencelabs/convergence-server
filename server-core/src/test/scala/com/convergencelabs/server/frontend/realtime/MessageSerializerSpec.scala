package com.convergencelabs.server.frontend.realtime

import scala.language.postfixOps

import org.junit.runner.RunWith
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner

class MessageSerializerSpec
    extends WordSpecLike
    with Matchers {

  "A MessageSerializer" when {
    val op = CompoundOperationData(List())
    val message = OperationSubmissionMessage("r", 1L, 2L, op)
    val envelop = MessageEnvelope(message, None, None)
    val json = MessageSerializer.writeJson(envelop)
    
    println(json)
    val deserialized = MessageSerializer.readJson[MessageEnvelope](json);

  }
}
