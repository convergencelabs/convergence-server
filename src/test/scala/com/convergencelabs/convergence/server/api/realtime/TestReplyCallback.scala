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

package com.convergencelabs.convergence.server.api.realtime

import com.convergencelabs.convergence.proto.{ResponseMessage, ServerMessage}
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import org.json4s.JsonAST.JValue
import scalapb.GeneratedMessage

import scala.concurrent.{Future, Promise}

class TestReplyCallback() extends ReplyCallback {
  private[this] val p = Promise[GeneratedMessage with ResponseMessage with ServerMessage]()

  def result: Future[GeneratedMessage with ResponseMessage with ServerMessage] = p.future

  def reply(message: GeneratedMessage with ResponseMessage with ServerMessage): Unit = {
    p.success(message)
  }

  def unknownError(): Unit = {
    p.failure(new IllegalStateException())
  }

  def unexpectedError(details: String): Unit = {
    p.failure(new IllegalStateException())
  }

  def expectedError(code: ErrorCodes.ErrorCode, message: String, details: Map[String, JValue]): Unit = {
    p.failure(new IllegalStateException())
  }

  def expectedError(code: ErrorCodes.ErrorCode, message: String): Unit = {
    p.failure(new IllegalStateException())
  }

  override def timeoutError(): Unit = {
    p.failure(new IllegalStateException())
  }
}
