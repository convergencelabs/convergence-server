package com.convergencelabs.server.domain.model.cluster

import akka.actor.ActorRef

sealed trait ModelMessage {
  val modelId: String;
}

sealed trait RestModelMessage extends ModelMessage
case class GetRealTimeModel(modelId: String) extends ModelMessage
case class DeleteRealTimeModel(modelId: String) extends ModelMessage
case class UpdateRealTimeModel(modelId: String) extends ModelMessage
case class CreateRealTimeModel(modelId: String) extends ModelMessage

sealed trait RealTimeModelMessage extends ModelMessage
case class OpenRealTimeModel(modelId: String, client: ActorRef) extends RealTimeModelMessage
case class CloseRealTimeModel(modelId: String, client: ActorRef) extends RealTimeModelMessage
