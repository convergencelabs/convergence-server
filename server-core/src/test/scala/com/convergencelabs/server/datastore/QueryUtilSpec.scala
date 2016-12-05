package com.convergencelabs.server.datastore

import java.util.ArrayList

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.orientechnologies.orient.core.record.impl.ODocument

// scalastyle:off multiple.string.literals
class QueryUtilSpec
    extends WordSpec
    with Matchers {

  val Field = "test"
  val Value = "value"
  val SampleDoc = new ODocument().field(Field, Value)

  "A QueryUtil" when {

    "mapping a singleton result list" must {

      "return a mapped object for a singleton list" in {
        val list = new ArrayList[ODocument]()
        list.add(SampleDoc)
        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
          val value: String = doc.field(Field)
          value
        }

        mappedValue shouldBe Some(Value)
      }

      "return None for an empty list" in {
        val list = new ArrayList[ODocument]()
        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
          val value: String = doc.field(Field)
          value
        }

        mappedValue shouldBe None
      }

      "return None for a list with multiple objects in it" in {
        val list = new ArrayList[ODocument]()
        list.add(new ODocument())
        list.add(new ODocument())
        val mappedValue = QueryUtil.mapSingletonList(list) { doc =>
          val value: String = doc.field(Field)
          value
        }

        mappedValue shouldBe None
      }
    }

    "mapping a singleton result list from an option" must {

      "return a mapped option object for a singleton list" in {
        val list = new ArrayList[ODocument]()
        list.add(SampleDoc)
        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
          val value: String = doc.field(Field)
          Some(value)
        }

        mappedValue shouldBe Some(Value)
      }

      "return None for an empty list" in {
        val list = new ArrayList[ODocument]()
        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
          val value: String = doc.field(Field)
          Some(value)
        }

        mappedValue shouldBe None
      }

      "return None for a list with multiple objects in it" in {
        val list = new ArrayList[ODocument]()
        list.add(new ODocument())
        list.add(new ODocument())
        val mappedValue = QueryUtil.mapSingletonListToOption(list) { doc =>
          val value: String = doc.field(Field)
          Some(value)
        }

        mappedValue shouldBe None
      }
    }

    "enforcing a singleton result list" must {

      "return a mapped object for a singleton list" in {
        val list = new ArrayList[ODocument]()
        list.add(SampleDoc)
        val mappedValue = QueryUtil.enforceSingletonResultList(list)
        mappedValue shouldBe Some(SampleDoc)
      }

      "return None for an empty list" in {
        val list = new ArrayList[ODocument]()
        val mappedValue = QueryUtil.enforceSingletonResultList(list)
        mappedValue shouldBe None
      }

      "return None for a list with multiple objects in it" in {
        val list = new ArrayList[ODocument]()
        list.add(new ODocument())
        list.add(new ODocument())
        val mappedValue = QueryUtil.enforceSingletonResultList(list)
        mappedValue shouldBe None
      }
    }
  }
}
