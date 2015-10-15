package com.convergencelabs.server

import java.net.URI
import org.java_websocket.drafts.Draft_10

object MockClient {
  def main(args: Array[String]): Unit = {
    var client = new EmptyClient(new URI("ws://localhost:8080/domain/foo/bar"), new Draft_10())
    client.connect()
  }
}