package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JObject

case class OperationPair(serverOp: JObject, clientOp: JObject)
case class OTFTestCase(id: String, input: OperationPair, output: Option[OperationPair], error: Option[Boolean])
