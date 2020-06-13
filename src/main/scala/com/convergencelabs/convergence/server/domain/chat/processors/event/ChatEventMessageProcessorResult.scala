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

package com.convergencelabs.convergence.server.domain.chat.processors.event

import com.convergencelabs.convergence.server.domain.chat.ChatState
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask

/**
 * The ChatEventMessageProcessorResult represents the result of processing a
 * ChatEventRequest message.  Processing the message will potentially compute
 * a new state for the chat, generally when message processing is successful.
 * The result will also contain a [[ReplyAndBroadcastTask]] which describes
 * the reply that should go back to the requester and a potential message to
 * broadcast to other members of the chat.
 *
 * @param newState An optional new state for the chat based on the processed
 *                 message. None indicates that no state update should occur.
 * @param task     The reply and broadcast task that specifies what to
 *                 communicate the requester and the other members of the chat.
 */
private[chat] final case class ChatEventMessageProcessorResult(newState: Option[ChatState],
                                                               task: ReplyAndBroadcastTask)
