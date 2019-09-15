package com.convergencelabs.server.domain

import java.time.Instant

case class JwtAuthKey(
  id: String,
  description: String,
  updated: Instant,
  key: String,
  enabled: Boolean)
