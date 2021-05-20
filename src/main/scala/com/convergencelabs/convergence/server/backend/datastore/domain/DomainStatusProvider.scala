package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainStatus.DomainStatus

import scala.util.Try

class DomainStatusProvider(domainStore: DomainStore, domainId: DomainId) {

  def getDomainStatus: Try[Option[DomainStatus]] = {
    domainStore.getDomain(domainId).map(_.map(_.status))
  }
}
