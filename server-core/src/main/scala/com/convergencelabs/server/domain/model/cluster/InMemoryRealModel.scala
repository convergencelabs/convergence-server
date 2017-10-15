package com.convergencelabs.server.domain.model.cluster

import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore

class InMemoryRealTimeModel(
    val onClosed: () => Unit,
    val modelStore: ModelStore,
    val modelOpProcessor: ModelOperationProcessor,
    val modelSnapshotStore: ModelSnapshotStore
    ) {
  var counter: Int = 0;
  
  def handleMessage: PartialFunction[RealTimeModelMessage, Unit] = {
    case msg: OpenRealTimeModel =>
      counter = counter + 1
    case msg: CloseRealTimeModel =>
      counter = counter - 1
      if (counter == 0) {
        onClosed()
      }
  }
  
  def forceClose(reason: String): Unit = {
    
  }
}
