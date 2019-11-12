/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.routing.RoundRobinGroup

object AkkaRouterUtils {
  def createBackendRouter(system: ActorSystem, relativePath: String, localName: String): ActorRef = {
    system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = 100, routeesPaths = List("/user/" + relativePath),
          allowLocalRoutees = true, useRoles = Set("backend"))).props(),
      name = localName)
  }
}
