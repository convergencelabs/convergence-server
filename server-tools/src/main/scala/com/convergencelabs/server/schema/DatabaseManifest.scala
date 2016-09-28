package com.convergencelabs.server.schema

case class DatabaseManifest(
  deltaDirectory: String,
  schemaVersion: Int,
  dataScripts: Option[List[String]])
