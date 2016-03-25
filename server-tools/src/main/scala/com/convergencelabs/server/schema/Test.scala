package com.convergencelabs.server.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

object Test extends App {
  val db = new ODatabaseDocumentTx(s"memory:test")
  db.activateOnCurrentThread()
  db.create()

  db.command(new OCommandSQL("CREATE class Model")).execute();
  db.command(new OCommandSQL("CREATE Property Model.id Integer")).execute();
  db.command(new OCommandSQL("INSERT Into Model SET id = 1")).execute();

  val result: java.util.List[ODocument] = db.query(new OSQLSynchQuery[ODocument]("SELECT @rid FROM Model where id = 1"));
  val doc = result.get(0)
  val rid: Object = doc.rawField("rid")
  println(rid)

  db.close();
}
