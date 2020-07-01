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

package com.convergencelabs.convergence.server.model.domain.chat

import java.time.Instant

final case class ChatInfo(id: String,
                          chatType: ChatType.Value,
                          created: Instant,
                          membership: ChatMembership.Value,
                          name: String,
                          topic: String,
                          lastEventNumber: Long,
                          lastEventTime: Instant,
                          members: Set[ChatMember])
