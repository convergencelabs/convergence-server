package com.convergencelabs.server.domain

import org.jose4j.jwt.JwtClaims
import scala.reflect.ClassTag

private[domain] object JwtUtil {
  def getClaim[T](jwtClaims: JwtClaims, claim: String)(implicit tag: ClassTag[T]): Option[T] = {
    if (jwtClaims.hasClaim(claim)) {
      val claimValue = jwtClaims.getClaimValue(claim)
      val c = tag.runtimeClass
      if (c.isAssignableFrom(claimValue.getClass)) {
        Some(claimValue.asInstanceOf[T])
      } else {
        None
      }
    } else {
      None
    }
  }
}
