package com.convergencelabs.server.domain.model.ot

import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.scalatest.FunSpec

class ArrayMoveRangeHelperExhaustiveSpec extends FunSpec {

  val ArrayLength = 15
  type MoveMoveCase = TransformationCase[ArrayMoveOperation, ArrayMoveOperation]

  def createArrayMovePairs(length: Int): List[MoveMoveCase] = {
    val ranges = MoveRangeGenerator.createRanges(length)
    for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      ArrayMoveOperation(List(), false, r1.fromIndex, r1.toIndex),
      ArrayMoveOperation(List(), false, r2.fromIndex, r2.toIndex))
  }

  describe(s"When evalauting two ArrayMoveOperation range relationships") {
    val cases = createArrayMovePairs(ArrayLength)
    cases.foreach { mmc =>
      it(s"a server ${mmc.serverOp} and a client ${mmc.clientOp} must have a calcuable relationship") {
        Try {
          ArrayMoveRangeHelper.getRangeRelationship(mmc.serverOp, mmc.clientOp)
        } match {
          case Success(_) =>
          case Failure(cause) => fail()
        }
      }
    }
  }
}
