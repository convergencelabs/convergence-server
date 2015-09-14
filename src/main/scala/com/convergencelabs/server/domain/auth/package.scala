package com.convergencelabs.server.domain

import akka.actor.ActorRef

package object auth {

  sealed trait AuthenticationRequest
  case class PasswordAuthRequest(username: String, password: String) extends AuthenticationRequest
  case class TokenAuthRequest(jwt: String) extends AuthenticationRequest

  case class AuthSuccess(username: String)
  case object AuthFailure
}