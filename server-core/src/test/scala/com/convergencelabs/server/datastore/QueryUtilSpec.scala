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
class QueryUtilSpec
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
  
  "A QueryUtil" when {

//    "mapping a singleton result list" must {
//
//      "return a mapped object for a singleton list" in {
//        val list = new ArrayList[ODocument]()
//        list.add(SampleDoc)
//        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
//          val value: String = doc.field(Field)
//          value
//        }
//
//        mappedValue shouldBe Some(Value)
//      }
//
//      "return None for an empty list" in {
//        val list = new ArrayList[ODocument]()
//        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
//          val value: String = doc.field(Field)
//          value
//        }
//
//        mappedValue shouldBe None
//      }
//
//      "return None for a list with multiple objects in it" in {
//        val list = new ArrayList[ODocument]()
//        list.add(new ODocument())
//        list.add(new ODocument())
//        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
//          val value: String = doc.field(Field)
//          value
//        }
//
//        mappedValue shouldBe None
//      }
//    }
//
//    "mapping a singleton result list from an option" must {
//
//      "return a mapped option object for a singleton list" in {
//        val list = new ArrayList[ODocument]()
//        list.add(SampleDoc)
//        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
//          val value: String = doc.field(Field)
//          Some(value)
//        }
//
//        mappedValue shouldBe Some(Value)
//      }
//
//      "return None for an empty list" in {
//        val list = new ArrayList[ODocument]()
//        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
//          val value: String = doc.field(Field)
//          Some(value)
//        }
//
//        mappedValue shouldBe None
//      }
//
//      "return None for a list with multiple objects in it" in {
//        val list = new ArrayList[ODocument]()
//        list.add(new ODocument())
//        list.add(new ODocument())
//        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
//          val value: String = doc.field(Field)
//          Some(value)
//        }
//
//        mappedValue shouldBe None
//      }
//    }
//
//    "enforcing a singleton result list" must {
//
//      "return a mapped object for a singleton list" in {
//        val list = new ArrayList[ODocument]()
//        list.add(SampleDoc)
//        val mappedValue = QueryUtil.enforceSingletonResultList(list)
//        mappedValue shouldBe Some(SampleDoc)
//      }
//
//      "return None for an empty list" in {
//        val list = new ArrayList[ODocument]()
//        val mappedValue = QueryUtil.enforceSingletonResultList(list)
//        mappedValue shouldBe None
//      }
//
//      "return None for a list with multiple objects in it" in {
//        val list = new ArrayList[ODocument]()
//        list.add(new ODocument())
//        list.add(new ODocument())
//        val mappedValue = QueryUtil.enforceSingletonResultList(list)
//        mappedValue shouldBe None
//      }
//    }
    
    "executing a command" must {
      "foo" in withDatabase { db =>  
         val element1: OElement = db.newElement(ClassName)
         element1.setProperty(Key1, "test1")
         element1.save()
         
         val element2: OElement = db.newElement(ClassName)
         element2.setProperty(Key1, "test2")
         element2.save()
         
         val rs = db.query(s"SELECT FROM ${ClassName}", Map())
         
         while(rs.hasNext()) {
           println(rs.next().toElement.getClass.getName)
         }
         
         db.commit()
         
         val rs2 = db.command(s"DELETE FROM TestClass WHERE key1 = 'test1'")
         
         while(rs2.hasNext()) {
           println(rs2.next().toElement.getProperty("count"))
         }
      }
    }
  }
  
  def withDatabase(testCode: ODatabaseDocument => Any) = {
    val dbName = "test-" + System.currentTimeMillis()
    orientDB.create(dbName, ODatabaseType.MEMORY);
    TryWithResource(orientDB.open(dbName,"admin","admin")) { db =>
      testCode(db)
    }
  }
}
