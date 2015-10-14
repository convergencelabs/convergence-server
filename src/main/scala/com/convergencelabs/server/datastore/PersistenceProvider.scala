package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider

trait PersistenceProvider {
  def convergenceConfigStore: ConfigurationStore
  def domainConfigStore: DomainConfigurationStore
}