package com.convergencelabs.server.datastore.convergence

import java.time.Instant
import com.convergencelabs.server.domain.DomainFqn

case class ConvergenceDelta(deltaNo: Int, value: String)
case class ConvergenceDeltaHistory(delta: ConvergenceDelta, status: String, message: Option[String], date: Instant)

case class DomainDelta(deltaNo: Int, value: String)
case class DomainDeltaHistory(domain: DomainFqn, delta: DomainDelta, status: String, message: Option[String], date: Instant)