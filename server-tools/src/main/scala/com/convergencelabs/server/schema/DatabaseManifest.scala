package com.convergencelabs.server.schema

case class DatabaseManifest(
  dbType: DBType.Value,
  schemaVersion: Int,
  dataScripts: Option[List[String]])
