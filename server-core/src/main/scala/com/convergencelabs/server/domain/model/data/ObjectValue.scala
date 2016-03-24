package com.convergencelabs.server.domain.model.data

case class ObjectValue(id: String, children: Map[String, DataValue]) extends DataValue
