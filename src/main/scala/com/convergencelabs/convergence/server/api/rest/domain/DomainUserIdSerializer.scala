package com.convergencelabs.convergence.server.api.rest.domain

import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}

import java.net.{URLDecoder, URLEncoder}

object DomainUserIdSerializer {
  def encodeDomainUserId(userId: DomainUserId): String = {
    userId.userType match {
      case DomainUserType.Normal if userId.username.contains(":") =>
        URLEncoder.encode(userId.username, "UTF-8")
      case DomainUserType.Normal =>
        userId.username
      case _ =>
        s"${userId.userType.toString}:${URLEncoder.encode(userId.username, "UTF-8")}"
    }
  }

  def decodeDomainUserId(userId: String): Either[String,DomainUserId] = {
    if (userId.contains(":")) {
      val parts = userId.split(":")
      DomainUserType.withNameOpt(parts(0)) match {
        case Some(userType) =>
          val username = URLDecoder.decode(parts(1), "UTF-8")
          Right(DomainUserId(userType, username))
        case None =>
          Left("Invalid user id format.  Unknown user type: " + parts(0))
      }
    } else {
      Right(DomainUserId.normal(URLDecoder.decode(userId, "UTF-8")))
    }
  }
}
