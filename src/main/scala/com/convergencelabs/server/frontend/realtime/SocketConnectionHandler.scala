package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.domain.DomainFqn

class SocketConnectionHandler {

  private var listeners: List[(DomainFqn, ConvergenceServerSocket) => Unit] = List()

  def fireOnSocketOpen(domainFqn: DomainFqn, socket: ConvergenceServerSocket): Unit = {
    listeners.foreach(listener => listener(domainFqn, socket))
  }

  def addListener(listener: (DomainFqn, ConvergenceServerSocket) => Unit): Unit = {
    listeners :+= listener
  }

  def removeListener(listener: (DomainFqn, ConvergenceServerSocket) => Unit) {
    listeners = listeners.filter(element => listener != element)
  }
}