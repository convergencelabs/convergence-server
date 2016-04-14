package com.convergencelabs.server.datastore

trait DomainDBController {
  def createDomain(): DBConfig
  def deleteDomain(id: String): Unit
}

case class DBConfig(dbName: String, username: String, password: String)
