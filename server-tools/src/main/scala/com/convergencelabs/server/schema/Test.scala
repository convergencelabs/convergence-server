package com.convergencelabs.server.schema

import org.json4s.DefaultFormats
import org.json4s.Extraction
import org.json4s.jackson.JsonMethods

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object Test {

  case class Person(firstName: String, lastName: String, age: Int)
  case class Group(name: String, members: List[Person])

  val groupYaml = """name: my group
members:
  - firstName: Michael
    lastName: MacFadden
    age: 36
  - firstName: Jim
    lastName: james
    age: 23
"""
  
  val mapper = new ObjectMapper(new YAMLFactory())
  implicit val f = DefaultFormats
  
  def main(args: Array[String]): Unit = {
    println(parseYaml[Group](groupYaml))
  }
  
  def parseYaml[A](yaml: String)(implicit mf: Manifest[A]): A = {
    val jsonNode = mapper.readTree(groupYaml)
    val jValue = JsonMethods.fromJsonNode(jsonNode)
    Extraction.extract[A](jValue)
  }
}
