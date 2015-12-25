package com.convergencelabs.server.domain.model.ot

case class ArrayMoveRange(fromIndex: Int, toIndex: Int)

object MoveRangeGenerator {
  def createRanges(length: Int): List[ArrayMoveRange] = {
    var result = List[ArrayMoveRange]()
    for (r <- 0 until length) {
      result = result ++ createRangesFromPositionToEnd(r, length)
    }
    result
  }

  private[this] def createRangesFromPositionToEnd(start: Int, length: Int): List[ArrayMoveRange] = {
    var result = List[ArrayMoveRange]()
    for (r <- start until length) {
      result = result :+ ArrayMoveRange(start, r)
      if (r != start) {
        result = result :+ ArrayMoveRange(r, start)
      }
    }

    result
  }
}
