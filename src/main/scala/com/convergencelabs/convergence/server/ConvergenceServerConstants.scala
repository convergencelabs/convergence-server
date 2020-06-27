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

/**
 * This class contains server string constants used by the core
 * server classes.
 */
object ConvergenceServerConstants {
  /**
   * String constants for various Akka baseConfig keys that are used during
   * initialization.
   */
  object AkkaConfig {
    val AkkaClusterRoles = "akka.cluster.roles"
    val AkkaClusterSeedNodes = "akka.cluster.seed-nodes"
  }

  /**
   * String constants for the environment variables that the Convergence Server
   * will look for when initializing.
   */
  object Environment {
    val ConvergenceServerRoles = "CONVERGENCE_SERVER_ROLES"
    val ConvergenceClusterSeeds = "CONVERGENCE_CLUSTER_SEEDS"
    val ConvergenceLog4jConfigFile = "CONVERGENCE_LOG4J_CONFIG_FILE"
  }

  /**
   * The name of the Akka ActorSystem.
   */
  val ActorSystemName: String = "Convergence"
}
