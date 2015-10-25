package com.convergencelabs.server

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import scala.compat.Platform

object Test {
  def main(args: Array[String]): Unit = {
    val dbPool = new OPartitionedDatabasePool("remote:localhost/test1", "root", "root")
    val db = dbPool.acquire()

    val doc = db.newInstance("test")

    val op = StringInsertOperation(List(), false, 1, "23")

    doc.field("time", Platform.currentTime)
    doc.field("op", op)
    db.save(doc)
    db.close()
  }
}