package com.convergencelabs.server.domain

import java.time.Instant

case class TokenPublicKey(
  id: String,
  name: String,
  description: String,
  keyDate: Instant,
  key: String,
  enabled: Boolean)
