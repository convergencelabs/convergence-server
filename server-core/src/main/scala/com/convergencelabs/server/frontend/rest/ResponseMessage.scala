package com.convergencelabs.server.frontend.rest

trait ResponseMessage {
  def ok: Boolean
}

object ErrorMessage {
  def apply(error: String): BasicResponseMessage = {
    BasicResponseMessage(false, Some(error))
  }
}

case class BasicResponseMessage(ok: Boolean, error: Option[String]) extends ResponseMessage
