package com.convergencelabs.server.datastore.convergence

case class NamespaceNotFoundException(namespace: String) extends Exception(s"A namespace with the id '${namespace}' does not exists.")
