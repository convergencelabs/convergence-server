package com.convergencelabs.server.api.rest

import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.convergencelabs.server.api.rest.KeyGenService.CreateTokenResponse
import com.convergencelabs.server.domain.JwtUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object KeyGenService {
  case class CreateTokenResponse(publicKey: String, privateKey: String)
}

class KeyGenService(
  private[this] val executionContext: ExecutionContext)
    extends JsonSupport {

  implicit val ec: ExecutionContext = executionContext

  def route(): Route = {
    pathPrefix("util" / "keygen") {
      pathEnd {
        get {
          complete(createKey())
        }
      }
    }
  }

  def createKey(): Future[RestResponse] = {
    Future {
      JwtUtil.createKey().flatMap { rsaJsonWebKey =>
        for {
          publicKey <- JwtUtil.getPublicCertificatePEM(rsaJsonWebKey)
          privateKey <- JwtUtil.getPrivateKeyPEM(rsaJsonWebKey)
        } yield {
          okResponse(CreateTokenResponse(publicKey, privateKey))
        }
      }
    }.flatMap {
      case Success(s) => Future.successful(s)
      case Failure(fail) => Future.failed(fail)
    }
  }
}
