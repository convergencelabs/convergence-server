package com.convergencelabs.server

import org.rogach.scallop.ScallopConf

private object ServerCLIConf {
  def apply(arguments: Seq[String]): ServerCLIConf = new ServerCLIConf(arguments)
}

private class ServerCLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Server Node 0.1.0 (c) 2015 Convergence Labs")
  banner("Usage: -c convergence-server-application.conf")

  val config = opt[String](
    short = 'c',
    argName = "config",
    descr = "The locatin of the source folder",
    required = false,
    default = Some("config/convergence-server-application.conf"))
    
    verify()
}
