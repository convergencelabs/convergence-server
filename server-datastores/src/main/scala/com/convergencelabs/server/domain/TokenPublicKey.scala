package com.convergencelabs.server.domain

import java.time.Instant

case class TokenPublicKey(
  id: String,
  name: String, // FIXME we don't need a name
  description: String,
  created: Instant,
  key: String,
  enabled: Boolean)
