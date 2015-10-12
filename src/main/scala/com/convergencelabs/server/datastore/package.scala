package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn

case class TokenPublicKey(id: String, name: String, description: String, keyDate: Long, key: String, enabled: Boolean)
case class TokenKeyPair(publicKey: String, privateKey: String)

case class DomainDatabaseConfig(uri: String, username: String, password: String)

case class DomainConfig(
  systemId: String,
  domainFqn: DomainFqn,
  displayName: String,
  dbConfig: DomainDatabaseConfig,
  keys: Map[String, TokenPublicKey],
  adminKeyPair: TokenKeyPair)