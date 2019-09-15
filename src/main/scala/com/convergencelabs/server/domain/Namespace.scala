package com.convergencelabs.server.domain

case class Namespace(id: String, displayName: String, userNamespace: Boolean)
case class NamespaceUpdates(id: String, displayName: String)
case class NamespaceAndDomains(id: String, displayName: String, domains: Set[Domain])
