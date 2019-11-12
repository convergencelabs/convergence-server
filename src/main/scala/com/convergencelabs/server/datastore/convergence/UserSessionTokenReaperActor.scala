/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.convergence

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.db.DatabaseProvider

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

object UserSessionTokenReaperActor {
  def props(dbProvider: DatabaseProvider): Props = Props(new UserSessionTokenReaperActor(dbProvider))

  case object CleanUpSessions
}

class UserSessionTokenReaperActor(dbProvider: DatabaseProvider) extends Actor with ActorLogging {

  log.debug("UserSessionTokenReaperActor initializing")
  
  import UserSessionTokenReaperActor._

  private[this] val userSessionTokenStore = new UserSessionTokenStore(dbProvider)

  implicit val ec = this.context.system.dispatcher
  this.context.system.scheduler.schedule(0 seconds, 5 minutes, self, CleanUpSessions)

  def receive: Receive = {
    case CleanUpSessions =>
      this.cleanExpiredSessions()
  }

  private[this] def cleanExpiredSessions(): Unit = {
    log.debug("Cleaning expired user session tokens")
    userSessionTokenStore.cleanExpiredTokens() recover {
      case cause: Throwable =>
        log.error(cause, "Error cleaning up expired user session tokens")
    }
  }
  
  override def postStop(): Unit = {
    log.debug("UserSessionTokenReaperActor stopping")
  }
}
