package com.convergencelabs.server.db.schema

case class DeltaIndex(preReleaseVersion: Int, releasedVersion: Int, deltas: Map[String, VersionHash])
case class VersionHash(delta: String, database: String)
