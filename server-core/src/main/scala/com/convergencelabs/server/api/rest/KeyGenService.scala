package com.convergencelabs.server.api.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.JwtUtil

import KeyGenService.CreateTokenResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object KeyGenService {
  case class CreateTokenResponse(publicKey: String, privateKey: String)
}

class KeyGenService(
  private[this] val executionContext: ExecutionContext)
    extends JsonSupport {

  implicit val ec = executionContext

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
