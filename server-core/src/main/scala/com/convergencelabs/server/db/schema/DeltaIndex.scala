package com.convergencelabs.server.db.schema

case class DeltaIndex(maxDelta: Int, maxReleasedDelta: Int, deltas: Map[String, String])
