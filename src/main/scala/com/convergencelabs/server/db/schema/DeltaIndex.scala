/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.schema

case class DeltaIndex(preReleaseVersion: Int, releasedVersion: Int, deltas: Map[String, VersionHash])
case class VersionHash(delta: String, database: String)
