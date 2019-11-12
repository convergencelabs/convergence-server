/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import com.lambdaworks.crypto.SCryptUtil

object PasswordUtil {

  private val CpuCost = 16384
  private val MemoryCost = 8
  private val Parallelization = 1

  def hashPassword(password: String): String = {
    SCryptUtil.scrypt(password, CpuCost, MemoryCost, Parallelization)
  }

  def checkPassword(password: String, hash: String): Boolean = {
    SCryptUtil.check(password, hash)
  }
}
