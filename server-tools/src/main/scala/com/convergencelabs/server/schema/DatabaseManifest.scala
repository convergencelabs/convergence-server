package com.convergencelabs.server.schema

case class DatabaseManifest(
  inherit: Option[String],
  schemaScripts: Option[List[String]],
  dataScripts: Option[List[String]])
