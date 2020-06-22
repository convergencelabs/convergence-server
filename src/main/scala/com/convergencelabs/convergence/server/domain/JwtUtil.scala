/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain

import java.io.StringWriter
import java.util.{List => JavaList}

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.jose4j.jwk.{RsaJsonWebKey, RsaJwkGenerator}
import org.jose4j.jwt.JwtClaims

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Try

final case class JwtInfo(username: String,
                         firstName: Option[String],
                         lastName: Option[String],
                         displayName: Option[String],
                         email: Option[String],
                         groups: Option[Set[String]])

object JwtUtil {

  val KeyBits = 2048

  def parseClaims(jwtClaims: JwtClaims): JwtInfo = {
    val username = jwtClaims.getSubject
    val firstName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.FirstName)
    val lastName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.LastName)
    val displayName = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.DisplayName)
    val email = JwtUtil.getClaim[String](jwtClaims, JwtClaimConstants.Email)
    val groups = JwtUtil.getClaim[JavaList[String]](jwtClaims, JwtClaimConstants.Groups).map(_.asScala.toSet)
    JwtInfo(username, firstName, lastName, displayName, email, groups)
  }

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

  def createKey(): Try[RsaJsonWebKey] = Try(RsaJwkGenerator.generateJwk(KeyBits))

  def getPrivateKeyPEM(webKey: RsaJsonWebKey): Try[String] = {
    Try(webKey.getPrivateKey).flatMap(key => toPemString(key))
  }

  def getPublicCertificatePEM(webKey: RsaJsonWebKey): Try[String] = {
    Try(webKey.getPublicKey).flatMap(key => toPemString(key))

  }

  private[this] def toPemString(obj: Any): Try[String] = Try {
    val writer = new StringWriter()
    val pemWriter = new JcaPEMWriter(writer)
    pemWriter.writeObject(obj)
    pemWriter.flush()
    pemWriter.close()
    writer.toString
  }
}
