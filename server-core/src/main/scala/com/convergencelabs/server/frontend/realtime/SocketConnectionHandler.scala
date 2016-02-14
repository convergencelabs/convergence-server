package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.domain.DomainFqn

/**
 * A utility class that allows users to be notified when a new realtime
 * connection is made.  The listeners will be notified with the newly
 * connected socket and the DomainFqn identifying the domain that the
 * client is connecting to.
 */
class SocketConnectionHandler {

  private[this] var listeners: List[(DomainFqn, ConvergenceServerSocket) => Unit] = List()

  /**
   * Notifies registered listeners of a new connection.
   *
   * @param domainFqn
   *   The identifier of the domain the connection was made to.
   * @param socket
   *   The newly created socket connection.
   */
  def fireOnSocketOpen(domainFqn: DomainFqn, socket: ConvergenceServerSocket): Unit = {
    listeners.foreach(listener => listener(domainFqn, socket))
  }

  /**
   * Adds a new listener to be notified of new connections.  Duplicate
   * additions of the same listener will be ignored.
   *
   * @param listener
   *   The new listener to be added.
   */
  def addListener(listener: (DomainFqn, ConvergenceServerSocket) => Unit): Unit = {
    if (!listeners.contains(listener)) {
      listeners :+= listener
    }
  }

  /**
   * removes a listener to be notified of new connections.
   *
   * @param listener
   *   The listener to be removed.
   */
  def removeListener(listener: (DomainFqn, ConvergenceServerSocket) => Unit): Unit = {
    listeners = listeners.filter(element => listener != element)
  }
}
