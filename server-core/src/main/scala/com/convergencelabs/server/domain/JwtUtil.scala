package com.convergencelabs.server.domain

import java.io.StringWriter

import scala.reflect.ClassTag
import scala.util.Try

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.jose4j.jwk.RsaJsonWebKey
import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jwt.JwtClaims

object JwtUtil {

  val KeyBits = 2048

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

  def getPrivateKeyPEM(webKey: RsaJsonWebKey): Try[String] = Try {
    val privateKey = webKey.getPrivateKey()
    val writer = new StringWriter()
    val pemWriter = new JcaPEMWriter(writer)
    pemWriter.writeObject(privateKey)
    pemWriter.flush()
    pemWriter.close()
    writer.toString()
  }

  def getPublicCertificatePEM(webKey: RsaJsonWebKey): Try[String] = Try {
    val publicKey = webKey.getPublicKey()
    val writer = new StringWriter()
    val pemWriter = new JcaPEMWriter(writer)
    pemWriter.writeObject(publicKey)
    pemWriter.flush()
    pemWriter.close()
    writer.toString()
  }
}
