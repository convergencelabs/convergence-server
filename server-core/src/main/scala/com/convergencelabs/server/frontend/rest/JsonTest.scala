package com.convergencelabs.server.frontend.rest

import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import org.json4s.DefaultFormats
import org.json4s.FieldSerializer

object JsonTest {

  def main(args: Array[String]): Unit = {
    implicit val serialization = Serialization
    implicit val formats = DefaultFormats + FieldSerializer[Msg]()
    
    
    println(write(SomeError("errorMessage")))
    println(write(SomeSuccess("some data")))
  }
}

trait Msg {
  def ok: Boolean
}

abstract class AbstractErrorResponse() extends Msg {
  val ok = false
}

abstract class AbstractSuccessResponse() extends Msg {
  val ok = true
}

case class SomeError(message: String) extends AbstractErrorResponse

case class SomeSuccess(someData: String) extends AbstractSuccessResponse
