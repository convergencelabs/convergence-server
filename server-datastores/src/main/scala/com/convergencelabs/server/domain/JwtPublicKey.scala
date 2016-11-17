package com.convergencelabs.server.domain

import java.time.Instant

case class JwtPublicKey(
  id: String,
  description: String,
  updated: Instant,
  key: String,
  enabled: Boolean)
