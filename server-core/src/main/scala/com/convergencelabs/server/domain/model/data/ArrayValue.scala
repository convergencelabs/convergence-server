package com.convergencelabs.server.domain.model.data

case class ArrayValue(id: String, children: List[DataValue]) extends DataValue
