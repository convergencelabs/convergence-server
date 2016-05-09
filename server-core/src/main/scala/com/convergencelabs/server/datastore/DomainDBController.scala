package com.convergencelabs.server.datastore

trait DomainDBController {
  def createDomain(importFile: Option[String]): DBConfig
  def deleteDomain(id: String): Unit
}

case class DBConfig(dbName: String, username: String, password: String)
