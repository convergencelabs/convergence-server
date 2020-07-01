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

package com.convergencelabs.convergence.server.backend.services.domain.model

import java.time.Instant

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedOperation
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

final case class ModelOperation(modelId: String,
                                version: Long,
                                timestamp: Instant,
                                userId: DomainUserId,
                                sessionId: String,
                                op: AppliedOperation)


final case class NewModelOperation(modelId: String,
                                   version: Long,
                                   timestamp: Instant,
                                   sessionId: String,
                                   op: AppliedOperation)