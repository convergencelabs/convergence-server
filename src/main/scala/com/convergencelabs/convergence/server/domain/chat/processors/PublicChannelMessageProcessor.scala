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

package com.convergencelabs.convergence.server.domain.chat.processors

import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatState


/**
 * Processes messages for a Public Chats.
 *
 * @param chatState        The current state of the chat.
 * @param chatStore        The chat persistence store
 * @param permissionsStore The permissions persistence store.
 */
private[chat] class PublicChannelMessageProcessor(chatState: ChatState,
                                                  chatStore: ChatStore,
                                                  permissionsStore: PermissionsStore)
  extends MembershipChatMessageProcessor(chatState, chatStore, permissionsStore) {
}
