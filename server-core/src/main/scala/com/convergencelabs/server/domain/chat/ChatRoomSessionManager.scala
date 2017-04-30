package com.convergencelabs.server.domain.chat

import akka.actor.ActorRef
import com.convergencelabs.server.domain.model.SessionKey

class ChatRoomSessionManager {
  
  var clients = Set[ActorRef]()
  var clientSessionMap = Map[ActorRef, SessionKey]()
  var sessionClientMap = Map[SessionKey, ActorRef]()
  var userSessionMap = Map[String, Set[SessionKey]]()

  def join(sk: SessionKey, client: ActorRef): Boolean = {
    val username = sk.uid
    val userSessions = this.userSessionMap.getOrElse(username, Set())
    val newSessions = userSessions + sk
    this.userSessionMap += (username -> newSessions)
    clientSessionMap += (client -> sk)
    sessionClientMap += (sk -> client)
    this.clients += client
    newSessions.size == 1
  }
  
  def remove(username: String): Unit = {
    this.userSessionMap.get(username) foreach { sessions => 
      sessions.foreach(leave(_))
    }
  }

  def leave(sk: SessionKey): Boolean = {
    val client = this.sessionClientMap.get(sk).getOrElse {
      throw new IllegalArgumentException("No such session")
    }
    leave(client)
  }

  def leave(client: ActorRef): Boolean = {
    val sk = this.clientSessionMap.get(client).getOrElse {
      throw new IllegalArgumentException("No such client")
    }
    val username = sk.uid
    val userSessions = this.userSessionMap.getOrElse(username, Set())
    val newSessions = userSessions - sk
    if (newSessions.isEmpty) {
      this.userSessionMap -= username
    } else {
      this.userSessionMap += (username -> newSessions)
    }

    clientSessionMap -= client
    sessionClientMap -= sk
    clients -= client
    newSessions.isEmpty
  }
  
  def connectedClients(): Set[ActorRef] = {
    clients
  }

  def getSession(client: ActorRef): Option[SessionKey] = clientSessionMap.get(client)
  def getClient(sk: SessionKey): Option[ActorRef] = sessionClientMap.get(sk)
}