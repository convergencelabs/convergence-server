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

package com.convergencelabs.convergence.server.domain

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[PasswordAuthRequest], name = "password"),
  new JsonSubTypes.Type(value = classOf[JwtAuthRequest], name = "jwt"),
  new JsonSubTypes.Type(value = classOf[ReconnectTokenAuthRequest], name = "reconnect"),
  new JsonSubTypes.Type(value = classOf[AnonymousAuthRequest], name = "anonymous")
))
sealed trait AuthenticationCredentials

final case class PasswordAuthRequest(username: String, password: String) extends AuthenticationCredentials

final case class JwtAuthRequest(jwt: String) extends AuthenticationCredentials

final case class ReconnectTokenAuthRequest(token: String) extends AuthenticationCredentials

final case class AnonymousAuthRequest(displayName: Option[String]) extends AuthenticationCredentials