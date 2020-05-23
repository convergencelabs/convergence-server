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

package com.convergencelabs.convergence.server.domain.activity

import org.json4s.JsonAST.JValue

class ActivityStateMap private[activity] () {
  private[this] var state = Map[String, Map[String, JValue]]()

  def setState(sessionId: String, key: String, value: JValue): Unit = {
    val sessionState = state(sessionId)
    state += (sessionId -> (sessionState + (key -> value)))
  }

  def removeState(sessionId: String, key: String): Unit = {
    val sessionState = state(sessionId)
    state += (sessionId -> (sessionState - key))
  }
  
  def clear(): Unit ={
    state = Map()
  }

  def getState: Map[String, Map[String, JValue]] = {
    state
  }

  def join(sessionId: String): Unit = {
    state += (sessionId -> Map[String, JValue]())
  }

  def leave(sessionId: String): Unit = {
    state -= sessionId
  }
}
