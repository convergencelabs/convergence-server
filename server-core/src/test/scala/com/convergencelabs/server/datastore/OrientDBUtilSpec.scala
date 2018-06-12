package com.convergencelabs.server.datastore

import java.util.ArrayList

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.orientechnologies.orient.core.db.ODatabaseType
import com.convergencelabs.server.util.TryWithResource
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.record.OElement

// scalastyle:off multiple.string.literals
class OrientDBUtilSpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll {

  
  val Field = "key"
  val Value = "value"
  val SampleDoc = new ODocument().field(Field, Value)
  
  val ClassName = "TestClass"
  val Key1 = "key1"
  val Key2 = "key2"

  val orientDB: OrientDB = new OrientDB("memory:QueryUtilSpec", OrientDBConfig.defaultConfig());
  
  override def afterAll() = {
    orientDB.close()
  }
  
  "A OrientDBUtil" when {
    
    "executing a query" must {
      "return the correct documents" in withDatabase { db =>  
         val element1: OElement = db.newElement(ClassName)
         element1.setProperty(Key1, "test1")
         element1.save()
         
         val element2: OElement = db.newElement(ClassName)
         element2.setProperty(Key1, "test2")
         element2.save()
         
         val query = s"SELECT FROM ${ClassName} WHERE ${Key1} = 'test1'"
         val docs = OrientDBUtil.query(db, query).get
         
         docs.size shouldBe 1
         val doc = docs(0)
         doc.getIdentity shouldBe element1.getIdentity
      }
    }
  }
  
  def withDatabase(testCode: ODatabaseDocument => Any) = {
    val dbName = "test-" + System.currentTimeMillis()
    orientDB.create(dbName, ODatabaseType.MEMORY);
    TryWithResource(orientDB.open(dbName,"admin","admin")) { db =>
      testCode(db)
    }.get
  }
}
