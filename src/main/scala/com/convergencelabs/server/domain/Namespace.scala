/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain

case class Namespace(id: String, displayName: String, userNamespace: Boolean)
case class NamespaceUpdates(id: String, displayName: String)
case class NamespaceAndDomains(id: String, displayName: String, domains: Set[Domain])
