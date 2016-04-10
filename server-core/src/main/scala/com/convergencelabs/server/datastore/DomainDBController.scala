package com.convergencelabs.server.datastore

trait DomainDBController {
  def createDomain(): DBConfig
  def deleteDomain(id: String): Unit
}

case class DBConfig(id: String, username: String, password: String)