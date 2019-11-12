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

package com.convergencelabs.convergence.server.util

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
