package com.convergencelabs.server

import org.rogach.scallop.ScallopConf

private object ServerCLIConf {
  def apply(arguments: Seq[String]): ServerCLIConf = new ServerCLIConf(arguments)
}

private class ServerCLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Server Node")
  banner("Usage: -c convergence-server.conf")

  val config = opt[String](
    short = 'c',
    argName = "config",
    descr = "The locatin of the server configuration file",
    required = false,
    default = Some("/etc/convergence/convergence-server.conf"))
    
    verify()
}
