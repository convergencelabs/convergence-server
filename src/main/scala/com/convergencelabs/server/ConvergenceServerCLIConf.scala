/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server

import org.rogach.scallop.{ScallopConf, ScallopOption}

/**
 * A command line argument processor based on Scallop.
 *
 * @param arguments The command line arguments passed to the [[ConvergenceServer]]
 */
private class ConvergenceServerCLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Convergence Server Node")
  banner("Usage: -c convergence-server.conf")

  val config: ScallopOption[String] = opt[String](
    short = 'c',
    argName = "config",
    descr = "The location of the Convergence Server configuration file",
    required = false,
    default = Some("/etc/convergence/convergence-server.conf"))

  verify()
}

private object ConvergenceServerCLIConf {
  def apply(arguments: Seq[String]): ConvergenceServerCLIConf = new ConvergenceServerCLIConf(arguments)
}
