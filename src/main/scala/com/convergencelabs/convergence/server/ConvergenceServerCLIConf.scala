/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server

import org.rogach.scallop.exceptions.RequiredOptionNotFound
import org.rogach.scallop.{ScallopConf, ScallopOption}

/**
 * A command line argument processor based on Scallop.
 *
 * @param arguments The command line arguments passed to the [[ConvergenceServer]]
 */
private final class ConvergenceServerCLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version(s"Convergence Server (${BuildInfo.version})")
  banner("Usage: -c <config-file-path>>")

  val config: ScallopOption[String] = opt[String](
    short = 'c',
    argName = "config",
    descr = "The location of the Convergence Server configuration file",
    required = true,
    default = None)

  verify()

  override def onError(e: Throwable): Unit = e match {
    case RequiredOptionNotFound(optionName) =>
      println(s"Error: Required option '$optionName' was not set.\n")
      printHelp()
      sys.exit(1)
    case e: Throwable =>
      super.onError(e)
  }
}

private[server] object ConvergenceServerCLIConf {
  def apply(arguments: Seq[String]): ConvergenceServerCLIConf = new ConvergenceServerCLIConf(arguments)
}
