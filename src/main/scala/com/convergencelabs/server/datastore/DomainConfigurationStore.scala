package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn

trait DomainConfigurationStore {
  def getDomainConfig(domainFqn: DomainFqn): DomainConfig
}