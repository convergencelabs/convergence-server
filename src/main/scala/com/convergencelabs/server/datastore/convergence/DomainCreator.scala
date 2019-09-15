package com.convergencelabs.server.datastore.convergence

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.provision.DomainProvisionerActor.ProvisionDomain
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.DomainStatus
import com.convergencelabs.server.util.ExceptionUtils
import com.typesafe.config.Config

import grizzled.slf4j.Logging
import scala.util.Success
import com.convergencelabs.server.datastore.InvalidValueExcpetion

abstract class DomainCreator(
    dbProvider: DatabaseProvider,
    config: Config,
    implicit val executionContext: ExecutionContext) extends Logging {
  
  val domainStore = new DomainStore(dbProvider)
  val configStore = new ConfigStore(dbProvider)
  val randomizeCredentials = config.getBoolean("convergence.persistence.domain-databases.randomize-credentials")
  import ConfigKeys._
  
  def createDomain(
      namespace: String,
      id: String,
      displayName: String,
      anonymousAuth: Boolean,
      ): Try[Future[Unit]] = {
    this.validate(namespace, id).flatMap { _ =>
      val dbName = Math.abs(UUID.randomUUID().getLeastSignificantBits).toString
      val (dbUsername, dbPassword, dbAdminUsername, dbAdminPassword) = randomizeCredentials match {
        case false =>
          ("writer", "writer", "admin", "admin")
        case true =>
          (UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString())
      }
  
      val domainId = DomainId(namespace, id)
      val domainDbInfo = DomainDatabase(dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword)
  
      val provisionRequest = ProvisionDomain(domainId, dbName, dbUsername, dbPassword, dbAdminUsername, dbAdminPassword, anonymousAuth)
      domainStore.createDomain(domainId, displayName, domainDbInfo).map { _ =>
        provisionDomain(provisionRequest)
        .mapTo[Unit]
        .map { _ =>
            debug(s"Domain created, setting status to online: $dbName")
            this.updateStatusAfterProvisiong(domainId, DomainStatus.Online)
         }.recover {
           case cause: Throwable =>
           error(s"Domain was not created successfully: $dbName", cause)
            val statusMessage = ExceptionUtils.stackTraceToString(cause)
            this.updateStatusAfterProvisiong(domainId, DomainStatus.Error, statusMessage)
            ()
         }
      }
    }
  }
  
  private[this] def validate(namespace: String, id: String): Try[Unit] = {
    if (namespace.isEmpty) {
      Failure(InvalidValueExcpetion("namespace", "The namespace can not be empty"))
    } else if (id.isEmpty) {
      Failure(InvalidValueExcpetion("id", "The domain's namespace can not be empty"))
    } else {
      val keys = List(Namespaces.Enabled, Namespaces.UserNamespacesEnabled, Namespaces.DefaultNamespace)
      configStore.getConfigs(keys).flatMap { configs =>
        val namespacesEnabled = configs(Namespaces.Enabled).asInstanceOf[Boolean]
        val userNamespacesEnabled = configs(Namespaces.UserNamespacesEnabled).asInstanceOf[Boolean]
        val defaultNamesapce = configs(Namespaces.DefaultNamespace).asInstanceOf[String]
        if (!namespacesEnabled && namespace != defaultNamesapce) {
          Failure(InvalidValueExcpetion("namespace", "When namespaces are disabled, you can only create domains in the default namespace."))
        } else if (!userNamespacesEnabled && namespace.startsWith("~")) {
          Failure(InvalidValueExcpetion("namespace", "User namespaces are disabled."))
        } else {
          Success(())
        }
      }
    }
  }
  
  private[this] def updateStatusAfterProvisiong(domainId: DomainId, status: DomainStatus.Value, statusMessage: String = ""): Unit = {
    domainStore
      .getDomainByFqn(domainId)
      .flatMap (_ match {
        case Some(domain) =>
          val updated = domain.copy(status = status, statusMessage = statusMessage)
          domainStore.updateDomain(updated)
        case None =>
          Failure(new IllegalStateException("Could not find domain after it was created to update its status."))
      })
      .recover {
        case cause: Throwable =>
          logger.error("Could not update domain status after creation", cause)
      }
  }
  
  def provisionDomain(request: ProvisionDomain): Future[Unit]
}