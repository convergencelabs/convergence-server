package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class DomainConfigurationStore(dbPool: OPartitionedDatabasePool) {
  
  def createDomainConfig(domainConfig: DomainConfig) = {}
  
  def domainExists(domainFqn: DomainFqn): Boolean = false
  
  def getDomainConfig(domainFqn: DomainFqn): Option[DomainConfig] = None
  
  def getDomainConfig(id: String): Option[DomainConfig] = None
  
  def getDomainConfigsInNamespace(namespace: String): List[DomainConfig] = List()
  
  def removeDomainConfig(id: String): Unit = {}
  
  def updateDomainConfig(newConfig: DomainConfig): Unit = {}
  
  def getDomainKey(fqn: DomainFqn, keyId: String): Option[TokenPublicKey] = None
  
  def getDomainKeys(fqn: DomainFqn): Map[String, TokenPublicKey] = Map()
  
  def addDomainKey(fqn: DomainFqn, key: TokenPublicKey): Boolean = false 
}