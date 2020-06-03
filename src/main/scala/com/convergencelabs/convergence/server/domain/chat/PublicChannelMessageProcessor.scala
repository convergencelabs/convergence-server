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

package com.convergencelabs.convergence.server.domain.chat

import akka.actor.typed.scaladsl.ActorContext


/**
 * Processes messages for a Public Chats.
 *
 * @param stateManager The state manager that controls the persistence of chat state.
 * @param context      The actor context that the ChatActors are deployed into.
 */
private[chat] class PublicChannelMessageProcessor(stateManager: ChatStateManager, context: ActorContext[_])
  extends MembershipChatMessageProcessor(stateManager, context) {
}
