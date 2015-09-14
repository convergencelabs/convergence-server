package com.convergencelabs.server.domain.auth

import scala.concurrent.Future
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.ConfigurationStore

class LdapDomainAuthenticationProvider(configStore: ConfigurationStore) extends InternalDomainAuthenticationProvider {
    def createNamespace(namespace: String): Future[Unit] = {
      Future.successful(Unit)
    }
    
    def removeNamespace(namespace: String): Future[Unit] = {
      Future.successful(Unit)
    }
    
    def namespaceExists(namespace: String): Future[Boolean] = {
      Future.successful(true)
    }
    
    def createDomain(domainFqn: DomainFqn): Future[Unit] = {
      Future.successful(Unit)
    }

    def removeDomain(domainFqn: DomainFqn): Future[Unit] = {
      Future.successful(Unit)
    }

    def domainExists(domainFqn: DomainFqn): Future[Boolean] = {
      Future.successful(true)
    }

    def createUser(domainFqn: DomainFqn, username: String): Future[Unit] = {
      Future.successful(Unit)
    }

    def createUser(domainFqn: DomainFqn, username: String, password: String): Future[Unit] = {
      Future.successful(Unit)
    }

    def removeUser(domainFqn: DomainFqn, username: String): Future[Unit] = {
      Future.successful(Unit)
    }
    
    def userExists(domainFqn: DomainFqn, username: String): Future[Boolean] = {
      Future.successful(true)
    }

    def setPassword(domainFqn: DomainFqn, username: String, password: String): Future[Unit] = {
      Future.successful(Unit)
    }

    def verfifyCredentials(domainFqn: DomainFqn, username: String, password: String): Future[Boolean] = {
      Future.successful(true)
    }

    def dispose(): Unit = {
      
    }
}