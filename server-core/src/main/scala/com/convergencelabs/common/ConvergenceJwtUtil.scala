package com.convergencelabs.common

import java.io.File
import java.io.StringReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Map.Entry

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.jose4j.lang.JoseException
import scala.util.Try
import java.io.Reader
import java.io.FileReader

import ConvergenceJwtUtil.Issuer
import ConvergenceJwtUtil.Audiance

object ConvergenceJwtUtil {

  val Issuer = "ConvergenceJwtUtil"
  val Audiance = "Convergence"

  Security.addProvider(new BouncyCastleProvider())

  def fromString(keyId: String, text: String): Try[ConvergenceJwtUtil] = {
    fromReader(keyId, new StringReader(text))
  }

  def fromFile(keyId: String, file: String): Try[ConvergenceJwtUtil] = {
    fromReader(keyId, new FileReader(new File(file)))
  }

  def fromFile(keyId: String, file: File): Try[ConvergenceJwtUtil] = {
    fromReader(keyId, new FileReader(file))
  }

  private[this] def fromReader(keyId: String, keyReader: Reader): Try[ConvergenceJwtUtil] = Try {
    val pemReader = new PemReader(keyReader)
    val obj = pemReader.readPemObject()
    pemReader.close()

    val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
    val privateKeySpec = new PKCS8EncodedKeySpec(obj.getContent())
    val privateKey = keyFactory.generatePrivate(privateKeySpec)
    new ConvergenceJwtUtil(keyId, privateKey)
  }
}

class ConvergenceJwtUtil(
    private[this] val keyId: String,
    private[this] val privateKey: PrivateKey) {

  var expirationMinutes = 10
  var notBeforeMinutes = 2

  def getExpirationMinutes(): Int = {
    expirationMinutes
  }

  def setExpirationMinutes(expirationMinutes: Int): Unit = {
    this.expirationMinutes = expirationMinutes
  }

  def getNotBeforeMinutes(): Int = {
    notBeforeMinutes
  }

  def setNotBeforeMinutes(notBeforeMinutes: Int): Unit = {
    this.notBeforeMinutes = notBeforeMinutes
  }

  def getPrivateKey(): PrivateKey = {
    privateKey
  }

  def getKeyId(): String = {
    keyId
  }

  def generateToken(username: String, claims: Map[String, Any] = Map()): Try[String] = Try {
    // Create the claims with the basic info.
    val jwtClaims = new JwtClaims()
    jwtClaims.setIssuer(Issuer)
    jwtClaims.setAudience(Audiance)
    jwtClaims.setGeneratedJwtId()
    jwtClaims.setExpirationTimeMinutesInTheFuture(expirationMinutes)
    jwtClaims.setIssuedAtToNow()
    jwtClaims.setNotBeforeMinutesInThePast(this.notBeforeMinutes)

    // Add claims the user is providing.
    jwtClaims.setSubject(username)

    // If they have other claims.
    claims.foreach(claim => {
      jwtClaims.setClaim(claim._1, claim._2)
    })

    // The JWS will be used to sign the payload.
    val jws = new JsonWebSignature()
    jws.setPayload(jwtClaims.toJson())
    jws.setKey(this.privateKey)

    // We set the Key Id so that the server knows which key to check against.
    jws.setKeyIdHeaderValue(this.keyId)
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256)
    jws.getCompactSerialization()
  }
}
