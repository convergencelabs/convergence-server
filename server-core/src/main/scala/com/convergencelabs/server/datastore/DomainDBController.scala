package com.convergencelabs.server.datastore

import com.convergencelabs.server.frontend.rest.DomainFqn

trait DomainDBController {
  def createDomain(): DBConfig
  def deleteDomain(id: String): Unit
}

case class DBConfig(id: String, username: String, password: String)