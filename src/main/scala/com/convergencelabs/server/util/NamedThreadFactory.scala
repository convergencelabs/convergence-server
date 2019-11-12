/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class NamedThreadFactory(val poolName: String) extends ThreadFactory {

  private[this] val threadNumber = new AtomicInteger(1)
  private[this] val namePrefix = poolName + "-thread-"

  private[this] val s = System.getSecurityManager()

  // scalastyle:off null
  private[this] val group = (s != null) match {
    case true => s.getThreadGroup()
    case false => Thread.currentThread().getThreadGroup()
  }
  // scalastyle:on null

  def newThread(r: Runnable): Thread = {
    var t = new Thread(
      group,
      r,
      namePrefix + threadNumber.getAndIncrement(),
      0)

    if (t.isDaemon()) {
      t.setDaemon(false)
    }

    if (t.getPriority() != Thread.NORM_PRIORITY) {
      t.setPriority(Thread.NORM_PRIORITY)
    }

    t
  }
}
