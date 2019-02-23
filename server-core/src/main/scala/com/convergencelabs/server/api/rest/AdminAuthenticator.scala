package com.convergencelabs.server.api.rest

import akka.http.scaladsl.server.directives.Credentials
import com.typesafe.config.Config

object AdminAuthenticator {
  def authenticate(config: Config)(credentials: Credentials): Option[String] = {
    credentials match {
      case provided @ Credentials.Provided(id) if config.hasPath(id) && provided.verify(config.getString(id)) => Some(id)
      case _ => None
    }
  }
}
