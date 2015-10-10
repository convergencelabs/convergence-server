package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn

trait DomainConfigurationStore {
  
  def domainExists(domainFqn: DomainFqn): Boolean
  
  def getDomainConfig(domainFqn: DomainFqn): DomainConfig
  
  
}