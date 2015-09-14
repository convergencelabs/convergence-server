package com.convergencelabs.server.domain.auth

import scala.concurrent.Future
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.ConfigurationStore

trait InternalDomainAuthenticationProvider {
    def createNamespace(namespace: String): Future[Unit]
    
    def removeNamespace(namespace: String): Future[Unit]
    
    def namespaceExists(namespace: String): Future[Boolean]
    
    def createDomain(domainFqn: DomainFqn): Future[Unit]

    def removeDomain(domainFqn: DomainFqn): Future[Unit]

    def domainExists(domainFqn: DomainFqn): Future[Boolean]

    def createUser(domainFqn: DomainFqn, username: String): Future[Unit]

    def createUser(domainFqn: DomainFqn, username: String,  password: String): Future[Unit]

    def removeUser(domainFqn: DomainFqn, username: String): Future[Unit]
    
    def userExists(domainFqn: DomainFqn, username: String): Future[Boolean]

    def setPassword(domainFqn: DomainFqn, username: String, password: String): Future[Unit]

    def verfifyCredentials(domainFqn: DomainFqn, username: String, password: String): Future[Boolean]

    def dispose(): Unit
}