package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.domain.model.RealtimeModelActor.ForceClose
import com.convergencelabs.server.domain.model.RealtimeModelActor.OperationCommitted
import com.convergencelabs.server.domain.model.RealtimeModelActor.StreamCompleted
import com.convergencelabs.server.domain.model.RealtimeModelActor.StreamFailure

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging

class RealtimeModelPersistenceStream(
    model: ActorRef, 
    implicit val system: ActorSystem, 
    modelOperationProcessor: ModelOperationProcessor)
    extends Logging {
  
  implicit val materializer = ActorMaterializer()
  
  val streamActor: ActorRef = 
     Flow[NewModelOperation]
      .map { modelOperation =>
        modelOperationProcessor.processModelOperation(modelOperation)
          .map { _ =>
            model ! OperationCommitted(modelOperation.version)
            ()
          }
          .recover {
            case cause: Exception =>
              // FIXME this is probably altering state outside of the thread.
              // probably need to send a message.
              error(s"Error applying operation: ${modelOperation}", cause)
              model ! ForceClose("There was an unexpected persistence error applying an operation.")
              ()
          }
      }.to(Sink.onComplete {
        case Success(_) =>
          // Note when we shut down we complete the persistence stream.
          // So after that is done, we kill ourselves.
          model ! StreamCompleted
        case Failure(f) =>
          // FIXME this is probably altering state outside of the thread.
          // probably need to send a message.
          error("Persistence stream completed with an error", f)
          model ! StreamFailure
      }).runWith(Source
        .actorRef[NewModelOperation](bufferSize = 1000, OverflowStrategy.fail))
}