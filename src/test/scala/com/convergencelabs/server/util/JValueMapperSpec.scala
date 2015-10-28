package com.convergencelabs.server.util

import org.scalatest.WordSpec
import org.json4s._
import java.math.BigInteger

class JValueMapperSpec extends WordSpec {


  "An JValueMapper" when {
    "mapping from JValues to Java" must {
      "convert a JInt containing an int to an integer" in {
        val converted = JValueMapper.jValueToJava(JInt(Int.MaxValue))
        assert(converted.getClass == classOf[java.lang.Integer])
        assert(converted == Int.MaxValue)
      }
      
      "convert a JInt containing a long to an Long" in {
        val converted = JValueMapper.jValueToJava(JInt(Long.MaxValue))
        assert(converted.getClass == classOf[java.lang.Long])
        assert(converted == Long.MaxValue)
      }
      
      "convert a JInt containing a BigInteger to an BigInteger" in {
        val bigInteger = BigInteger.valueOf(Long.MaxValue)
        val value = bigInteger.add(bigInteger)   
        val bi = new BigInt(value)
        assert(JValueMapper.jValueToJava(JInt(bi)) == value)
      }
      
      
      "convert a JDouble containing a double to an Double" in {
        val converted = JValueMapper.jValueToJava(JDouble(Double.MaxValue))
        assert(converted.getClass == classOf[java.lang.Double])
        assert(converted == Double.MaxValue)
      }
      
      "convert a JBool containing false to false" in {
        assert(JValueMapper.jValueToJava(JBool(false)) == false)
      }
      
      "convert a JBool containing true to true" in {
        assert(JValueMapper.jValueToJava(JBool(true)) == true)
      }
      
      
      "convert a JString to the correct string" in {
        assert(JValueMapper.jValueToJava(JString("test")) == "test")
      }
      
      
      "convert a JNull to null" in {
        assert(JValueMapper.jValueToJava(JNull) == null)
      }
      
      
      "convert a JDecimal to a BigDecimal" in {
        val decimal = BigDecimal(2.3D)
        assert(JValueMapper.jValueToJava(JDecimal(decimal)) == decimal)
      }
    }
  }
}