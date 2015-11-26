package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.scalatest.Finders
import org.scalatest.WordSpec

class ArraySetPTFSpec extends WordSpec {

  "A ArraySetPTF" when {
    "tranforming a descendant path" must {
      "obsolete the path of the descendand." in {
        val ancestor = ArraySetOperation(List(1, 1), false, JArray(List(JInt(1))))
        val path = List(1, 1, 2, 1)
        val result = ArraySetPTF.transformDescendantPath(ancestor, path)
        assert(result == PathObsoleted)
      }
    }
  }
}