package com.convergencelabs.server.db.data

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.schema.DomainDBProvider
import scala.util.Try
import java.time.Duration
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.DomainStore

class ConvergenceImporter(
    private[this] val dbPool: OPartitionedDatabasePool,
    private[this] val domainDbProvider: DomainDBProvider,
    private[this] val data: ImportScript) {

  def importData(): Try[Unit] = {
    importUsers() flatMap (_ =>
      importDomains())
  }

  def importUsers(): Try[Unit] = Try {
    val userStore = new UserStore(dbPool, Duration.ofMillis(0L))
    data.users foreach {
      _.map { userData =>
        val user = User(
          userData.username,
          userData.email,
          userData.firstName.getOrElse(""),
          userData.lastName.getOrElse(""))
        userStore.createUser(user, userData.password) recover {case e: Exception => throw e}
      }
    }
  }

  def importDomains(): Try[Unit] = Try {
    val domainStore = new DomainStore(dbPool)
  }
}