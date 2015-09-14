package com.convergencelabs.server.domain.model.ot.ops

trait Operation {
  def invert(): Operation
}
