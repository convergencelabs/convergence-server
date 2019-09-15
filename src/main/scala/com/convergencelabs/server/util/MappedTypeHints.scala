package com.convergencelabs.server.util

import org.json4s.TypeHints

case class MappedTypeHits(private val hintMap: Map[String, Class[_]]) extends TypeHints {
  private val reverseHintMap = hintMap map (_.swap)

  val hints: List[Class[_]] = hintMap.values.toList

  def hintFor(clazz: Class[_]): String = reverseHintMap(clazz)
  def classFor(hint: String): Option[Class[_]] = hintMap.get(hint)
}
