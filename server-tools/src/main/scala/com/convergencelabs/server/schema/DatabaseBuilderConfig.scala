package com.convergencelabs.server.schema

case class DatabaseBuilderConfig(
  outputFile: String,
  schemaScripts: Option[List[String]],
  dataScripts: Option[List[String]])
