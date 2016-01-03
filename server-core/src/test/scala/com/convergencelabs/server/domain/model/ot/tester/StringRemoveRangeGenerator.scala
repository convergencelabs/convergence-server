package com.convergencelabs.server.domain.model.ot

case class StringDeleteRange(index: Int, value: String)

object StringRemoveRangeGenerator {
  def createDeleteRanges(modelValue: String): List[StringDeleteRange] = {
    var result = List[StringDeleteRange]()
    for (r <- 0 until modelValue.length) {
      result = result ++ createRangesFromPositionToEnd(r, modelValue)
    }
    result
  }

  private[this] def createRangesFromPositionToEnd(start: Int, modelValue: String): List[StringDeleteRange] = {
    val range = (start + 1 until modelValue.length).toList
    for {
      r <- range
    } yield StringDeleteRange(start, modelValue.substring(start, r))
  }
}
