package com.convergencelabs.server.domain.model.ot

import scala.util.Try
import scala.util.Success
import scala.util.Failure

object ArrayMoveRangeHelperTester extends App {
  type MoveMoveCase = TransformationCase[ArrayMoveOperation, ArrayMoveOperation]
  
  def createArrayMovePairs(length: Int): List[MoveMoveCase] = {
    var ranges = MoveRangeGenerator.createRanges(length)
    val crossProduct = for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      ArrayMoveOperation(List(), false, r1.fromIndex, r1.toIndex),
      ArrayMoveOperation(List(), false, r2.fromIndex, r2.toIndex))
    crossProduct
  }
  
  val max = 50
  val initial = (0 to max).toList

  println(s"Generating all permutation of moves for an array of length: ${max}")
  val cases = createArrayMovePairs(max)
  println(s"Generated ${cases.length} cases")

  var passed = scala.collection.mutable.ListBuffer[MoveMoveCase]()
  var failed = scala.collection.mutable.ListBuffer[MoveMoveCase]()

  cases.foreach { mmc =>
    Try {
      ArrayMoveRangeHelper.getRangeRelationship(mmc.serverOp, mmc.clientOp)
    } match {
      case Success(x) => passed += mmc
      case Failure(x) => failed += mmc
    }
  }

  println(s"Tests performed: ${cases.length}")
  println(s"Tests passed: ${passed.length}")
  println(s"Tests failed: ${failed.length}")
}
