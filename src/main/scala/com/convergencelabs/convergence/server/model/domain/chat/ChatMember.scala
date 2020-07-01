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

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

/**
 * Represents the state of a user within a chat.
 *
 * @param chatId       The id of the chat this member is a part of.
 * @param userId       The id of the user that is in the chat.
 * @param maxSeenEvent The maximum event this user has seen.
 */
final case class ChatMember(chatId: String, userId: DomainUserId, maxSeenEvent: Long)
