package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn

class DomainConfigurationStore {
  
  def createDomainConfig(domainConfig: DomainConfig) = ???
  
  def domainExists(domainFqn: DomainFqn): Boolean = ???
  
  def getDomainConfig(domainFqn: DomainFqn): DomainConfig = ???
  
  def getDomainConfig(id: String): Unit = ???
  
  def getDomainConfigsInNamespace(namespace: String): List[DomainConfig] = ???
  
  def removeDomainConfig(id: String): Unit = ???
  
  def updateDomainConfig(newConfig: DomainConfig): Unit = ???
  
  def getDomainKey(fqn: DomainFqn, keyId: String): TokenPublicKey = ???
  
  def getDomainKeys(fqn: DomainFqn): Map[String, TokenPublicKey] = ???
  
  def addDomainKey(fqn: DomainFqn, key: TokenPublicKey): Boolean = ???
}