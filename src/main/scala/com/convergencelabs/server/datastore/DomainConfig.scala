package com.convergencelabs.server.datastore

import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.DomainFqn

case class DomainConfig(
  systemId: String,
  domainFqn: DomainFqn,
  displayName: String,
  dbConfig: JValue,
  keys: Map[String, TokenPublicKey],
  adminKeyPair: TokenKeyPair)
  