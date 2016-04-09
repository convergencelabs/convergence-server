package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.AuthStoreActor._
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import akka.actor.Props
import akka.actor.Status

import ReplyUtil.ReplyTry

abstract class StoreActor private[datastore] extends Actor with ActorLogging {
  def reply[T](value: Try[T])(mapper: PartialFunction[T, Any]): Unit = {
    sender ! (value mapReply(mapper))
  }
}
