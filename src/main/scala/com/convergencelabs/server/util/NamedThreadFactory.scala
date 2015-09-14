package com.convergencelabs.server.util

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class NamedThreadFactory(val poolName: String) extends ThreadFactory {
  
  private[this] val threadNumber = new AtomicInteger(1);
  private[this] val namePrefix = poolName + "-thread-";

  private[this] val s = System.getSecurityManager();
  private[this] val group = (s != null) match {
    case true => s.getThreadGroup()
    case false => Thread.currentThread().getThreadGroup();
  }

  def newThread(r: Runnable): Thread = {
    var t = new Thread(
      group,
      r,
      namePrefix + threadNumber.getAndIncrement(),
      0);

    if (t.isDaemon()) {
      t.setDaemon(false);
    }

    if (t.getPriority() != Thread.NORM_PRIORITY) {
      t.setPriority(Thread.NORM_PRIORITY);
    }

    return t;
  }
}