package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.StringDeleteOperation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringOperationTransformer {
  def transformInsertInsert(op1: StringInsertOperation, op2: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    if (op2.index <= op1.index) {
      // The second inserts offset wins the tie break.
      // Shifts the first insert to the right by the length of the second insert.
      val builder = op1.copyBuilder()
      builder.index = op1.index + op2.value.length()
      (builder.build(), op2)
    } else {
      // Shifts the second insert to the right by the length of the first insert.
      val builder = op2.copyBuilder()
      builder.index = op2.index + op1.value.length()
      (op1, builder.build())
    }
  }

  def transformInsertDelete(op1: StringInsertOperation, op2: StringDeleteOperation): (DiscreteOperation, DiscreteOperation) = {
    lazy val op1Builder = op1.copyBuilder()
    lazy val op2Builder = op2.copyBuilder()

    if (op1.index <= op2.index) {
      op2Builder.index = op2.index + op1.value.length()
      (op1, op2Builder.build())
    } else if (op2.index + op2.value.length() <= op1.index) {
      op1Builder.index = op1.index - op2.value.length()
      (op1Builder.build(), op2)
    } else {
      op2Builder.noOp = true
      val offsetDelta = op1.index - op2.index
      op2Builder.value = op2.value.substring(0, offsetDelta) + op1.value + op2.value.substring(offsetDelta, op2.value.length())
      (op1Builder.build(), op2Builder.build())
    }
  }

  def transformInsertSet(op1: StringInsertOperation, op2: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    val op1Builder = op1.copyBuilder();
    op1Builder.noOp = true
    (op1Builder.build(), op2)
  }

  def transformDeleteInsert(op1: StringDeleteOperation, op2: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    if (op2.index <= op1.index) {
      val builder = op1.copyBuilder()
      builder.index = op1.index + op2.value.length()
      (builder.build(), op2)
    } else if (op1.index + op1.value.length() <= op2.index) {
      val builder = op2.copyBuilder()
      builder.index = op2.index - op1.value.length()
      (op1, builder.build())
    } else {
      val op1Builder = op1.copyBuilder()
      val op2builder = op2.copyBuilder()

      //Update op1 to include the inserted text
      val offsetDelta = op2.index - op1.index

      op1Builder.value = op1.value.substring(0, offsetDelta) + op2.value + op1.value.substring(offsetDelta, op1.value.length())

      op2builder.noOp = true
      (op1Builder.build(), op2builder.build())
    }
  }

  def transformDeleteDelete(op1: StringDeleteOperation, op2: StringDeleteOperation): (DiscreteOperation, DiscreteOperation) = {
    lazy val op1Builder = op1.copyBuilder()
    lazy val op2Builder = op2.copyBuilder()

    val op2Start = op2.index;
    val op2End = op2.index + op2.value.length();

    val op1Start = op1.index;
    val op1End = op1.index + op1.value.length();

    if (op1Start == op2Start) {
      if (op1End == op2End) {
        op1Builder.noOp = true
        op2Builder.noOp = true
        (op1Builder.build(), op2Builder.build())
      } else if (op2End > op1End) {
        op1Builder.noOp = true
        op2Builder.value = op2.value.substring(op1.value.length(), op2.value.length())
        (op1Builder.build(), op2Builder.build())
      } else {
        op2Builder.noOp = true
        op1Builder.value = op1.value.substring(op2.value.length(), op1.value.length())
        (op1Builder.build(), op2Builder.build())
      }

    } else if (op1Start > op2Start) {
      if (op2End < op1Start) {
        op1Builder.index = op1.index - op2.value.length()
        (op1Builder.build(), op2)
      } else if (op2End == op1End) {
        op1Builder.noOp = true
        op2Builder.value = op2.value.substring(0, op2.value.length() - op1.value.length())
        (op1Builder.build(), op2Builder.build())
      } else if (op2End > op1End) {
        op1Builder.noOp = true
        val overlapStart = op1.index - op2.index
        val overlapEnd = overlapStart + op1.value.length()
        op2Builder.value = op2.value.substring(0, overlapStart) + op2.value.substring(overlapEnd, op2.value.length())
        (op1Builder.build(), op2Builder.build())
      } else {
        val offsetDelta = op1.index - op2.index
        op1Builder.index = op2.index
        op1Builder.value = op1.value.substring(op2.value.length() - offsetDelta, op1.value.length())
        op2Builder.value = op2.value.substring(0, offsetDelta)
        (op1Builder.build(), op2Builder.build())
      }
    } else {
      if (op1End < op2Start) {
        op2Builder.index = op2.index - op1.value.length()
        (op1, op2Builder.build())
      } else if (op2End == op1End) {
        op2Builder.noOp = true
        op1Builder.value = op1.value.substring(0, op1.value.length() - op2.value.length())
        (op1Builder.build(), op2Builder.build())
      } else if (op1End > op2End) {
        op2Builder.noOp = true
        val overlapStart = op2.index - op1.index
        val overlapEnd = overlapStart + op2.value.length();

        op1Builder.value = op1.value.substring(0, overlapStart) + op1.value.substring(overlapEnd, op1.value.length())
        (op1Builder.build(), op2Builder.build())
      } else {
        val offsetDelta = op2.index - op1.index
        op2Builder.index = op1.index
        op2Builder.value = op2.value.substring(op1.value.length() - offsetDelta, op2.value.length())
        op1Builder.value = op1.value.substring(0, offsetDelta)
        (op1Builder.build(), op2Builder.build())
      }
    }
  }

  def transformDeleteSet(op1: StringDeleteOperation, op2: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    val op1Builder = op1.copyBuilder();
    op1Builder.noOp = true
    (op1Builder.build(), op2)
  }

  def transformSetInsert(op1: StringSetOperation, op2: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    val op2Builder = op2.copyBuilder();
    op2Builder.noOp = true
    (op1, op2Builder.build())
  }

  def transformSetDelete(op1: StringSetOperation, op2: StringDeleteOperation): (DiscreteOperation, DiscreteOperation) = {
    val op2Builder = op2.copyBuilder();
    op2Builder.noOp = true
    (op1, op2Builder.build())
  }

  def transformSetSet(op1: StringSetOperation, op2: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    val op1Builder = op1.copyBuilder();
    val op2Builder = op2.copyBuilder();
    op2Builder.noOp = true

    if (op1.newValue == op2.newValue) {
      op1Builder.noOp = true
    } else {
      op1Builder.oldValue == op2.newValue
    }

    (op1Builder.build(), op2Builder.build())
  }
}