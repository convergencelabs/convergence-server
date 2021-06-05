package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.convergence.DomainStore
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainState

import scala.util.Try

class DomainStateProvider(domainStore: DomainStore, domainId: DomainId) {

  def getDomainState(): Try[Option[DomainState]] = {
    domainStore.findDomain(domainId).map(_.map{ d =>
      DomainState(d.domainId, d.availability, d.status)
    })
  }
}
