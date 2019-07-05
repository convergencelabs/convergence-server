package com.convergencelabs.server.domain.activity

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

  def getState(): Map[String, Map[String, JValue]] = {
    state
  }

  def join(sessionId: String): Unit = {
    state += (sessionId -> Map[String, JValue]())
  }

  def leave(sessionId: String): Unit = {
    state -= sessionId
  }
}
