package com.convergencelabs.server.datastore.orient

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.domain.model.ModelFqn
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JBool

object orienttesting {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  val dbPool = new OPartitionedDatabasePool("remote:localhost/convergence", "root", "root")
                                                  //> Sep 24, 2015 8:42:25 PM com.orientechnologies.common.log.OLogManager log
                                                  //| INFO: OrientDB auto-config DISKCACHE=4,316MB (heap=1,819MB os=8,183MB disk=3
                                                  //| 90,837MB)
                                                  //| dbPool  : com.orientechnologies.orient.core.db.OPartitionedDatabasePool = co
                                                  //| m.orientechnologies.orient.core.db.OPartitionedDatabasePool@1cb346ea
	val modelStore = new OrientDBModelStore(dbPool)
                                                  //> modelStore  : com.convergencelabs.server.datastore.orient.OrientDBModelStore
                                                  //|  = com.convergencelabs.server.datastore.orient.OrientDBModelStore@57ad2aa7
	modelStore.createModel(ModelFqn("tests", "test"), JObject(List(("field1", JString("Testing Here")), ("field2", JBool(false)))), 2)
}