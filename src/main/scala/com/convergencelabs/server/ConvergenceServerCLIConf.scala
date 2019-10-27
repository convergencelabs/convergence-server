package com.convergencelabs.server

import org.rogach.scallop.{ScallopConf, ScallopOption}

private object ConvergenceServerCLIConf {
  def apply(arguments: Seq[String]): ConvergenceServerCLIConf = new ConvergenceServerCLIConf(arguments)
}

private class ConvergenceServerCLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Server Node")
  banner("Usage: -c convergence-server.conf")

  val config: ScallopOption[String] = opt[String](
    short = 'c',
    argName = "config",
    descr = "The locatin of the server configuration file",
    required = false,
    default = Some("/etc/convergence/convergence-server.conf"))

  verify()
}
