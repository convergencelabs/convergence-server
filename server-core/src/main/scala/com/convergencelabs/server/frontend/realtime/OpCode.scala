package com.convergencelabs.server.frontend.realtime

object OpCode extends Enumeration {
  val Ping = "ping"
  val Pong = "pong"
  val Normal = "norm"
  val Request = "rqst"
  val Reply = "rply"
}
