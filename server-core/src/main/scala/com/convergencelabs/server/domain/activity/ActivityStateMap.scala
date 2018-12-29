package com.convergencelabs.server.domain.activity

import com.convergencelabs.server.domain.model.SessionKey

class ActivityStateMap private[activity] () {
  private[this] var state = Map[SessionKey, Map[String, String]]()

  def setState(sk: SessionKey, key: String, value: String): Unit = {
    val sessionState = state(sk)
    state += (sk -> (sessionState + (key -> value)))
  }

  def clearState(sk: SessionKey, key: String): Unit = {
    val sessionState = state(sk)
    state += (sk -> (sessionState - key))
  }

  def getState(): Map[SessionKey, Map[String, String]] = {
    state
  }

  def join(sk: SessionKey): Unit = {
    state += (sk -> Map[String, String]())
  }

  def leave(sk: SessionKey): Unit = {
    state -= sk
  }
}
