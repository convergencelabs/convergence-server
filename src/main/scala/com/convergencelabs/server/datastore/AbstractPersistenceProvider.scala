/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore

import scala.util.Try
import com.convergencelabs.server.db.DatabaseProvider

abstract class AbstractPersistenceProvider(dbProvider: DatabaseProvider) {
  def validateConnection(): Try[Unit] = {
    dbProvider.validateConnection()
  }

  def shutdown(): Unit = {
    dbProvider.shutdown()
  }
}
