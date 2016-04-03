package com.convergencelabs.server.frontend.rest

import java.util.UUID

import akka.actor.Actor

sealed trait AuthResponse
case class AuthSuccess(token: String) extends AuthResponse
case object AuthFailure extends AuthResponse

case class AuthRequest(username: String, password: String)

class AuthRestActor extends Actor {
  def receive = {
    case AuthRequest(username, password) =>
      if (username == "user" && password == "password") {
        sender ! AuthSuccess(UUID.randomUUID().toString())
      } else {
        sender ! AuthFailure
      }
  }
}