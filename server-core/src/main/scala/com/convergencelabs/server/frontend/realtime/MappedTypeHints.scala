package com.convergencelabs.server.frontend.realtime

import org.json4s.TypeHints

case class MappedTypeHits(private val hintMap: Map[String, Class[_]]) extends TypeHints {
  private val reverseHintMap = hintMap map (_.swap)

  val hints: List[Class[_]] = hintMap.values.toList

  def hintFor(clazz: Class[_]) = reverseHintMap(clazz)
  def classFor(hint: String) = hintMap.get(hint)
}
