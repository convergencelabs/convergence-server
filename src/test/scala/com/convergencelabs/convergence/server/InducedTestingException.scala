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

package com.convergencelabs.convergence.server

import scala.util.control.NoStackTrace

/**
 * A utility class used when any exception will do for testing error cases
 * during unit testing. This exception has no stack trace in order to
 * not clutter the console and to improve performance.
 *
 * @param message An optional message for the exception.
 */
final case class InducedTestingException(message: String = "induced error for testing")
  extends RuntimeException(message)
    with NoStackTrace
