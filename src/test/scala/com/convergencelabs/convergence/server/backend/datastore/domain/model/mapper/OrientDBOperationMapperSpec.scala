/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.model.domain.model._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OrientDBOperationMapperSpec
    extends AnyWordSpec
    with Matchers {

  val valueId = "vid"

  private val complexJsonArray = ArrayValue("complexJsonArray", List(
    StringValue("av-1", "A String"),
    DoubleValue("av-2", 2),
    BooleanValue("av-3", value = true),
    NullValue("av-4"),
    DoubleValue("av-5", 5),
    DoubleValue("av-6", 9),
    ObjectValue("av-7", Map("key" -> StringValue("av7-1", "value")))))

  private val complexJsonObject = ObjectValue("complexJsonObject", Map(
    "array" -> complexJsonArray,
    "int" -> DoubleValue("ov-1", 4),
    "decimal" -> DoubleValue("ov-2", 6D),
    "double" -> DoubleValue("ov-3", 10D),
    "string" -> StringValue("ov-4", "another string"),
    "null" -> NullValue("ov-5"),
    "boolean" -> BooleanValue("ov-6", value = false),
    "object" -> ObjectValue("ov-7", Map("something" -> DoubleValue("ov7-1", 2)))))

  "An OrientDBOperationMapper" when {
    "converting string operations" must {
      "correctly map and unmap an StringInsertOperation" in {
        val op = AppliedStringInsertOperation(valueId, noOp = true, 3, "inserted")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringRemoveOperation" in {
        val op = AppliedStringRemoveOperation(valueId, noOp = true, 3, 7, Some("removed"))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringSetOperation" in {
        val op = AppliedStringSetOperation(valueId, noOp = true, "something", Some("old"))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting array operations" must {
      "correctly map and unmap an ArrayInsertOperation" in {
        val op = AppliedArrayInsertOperation(valueId, noOp = true, 3, complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayRemoveOperation" in {
        val op = AppliedArrayRemoveOperation(valueId, noOp = true, 3, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayReplaceOperation" in {
        val op = AppliedArrayReplaceOperation(valueId, noOp = true, 3, complexJsonArray, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayMoveOperation" in {
        val op = AppliedArrayMoveOperation(valueId, noOp = true, 3, 5)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArraySetOperation" in {
        val op = AppliedArraySetOperation(valueId, noOp = true, complexJsonArray.children, Some(List[DataValue]()))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting object operations" must {
      "correctly map and unmap an ObjectSetPropertyOperation" in {
        val op = AppliedObjectSetPropertyOperation(valueId, noOp = true, "setProp", complexJsonObject, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectAddPropertyOperation" in {
        val op = AppliedObjectAddPropertyOperation(valueId, noOp = true, "addProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectRemovePropertyOperation" in {
        val op = AppliedObjectRemovePropertyOperation(valueId, noOp = true, "removeProp", Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectSetOperation" in {
        val op = AppliedObjectSetOperation(valueId, noOp = true, complexJsonObject.children, Some(Map[String, DataValue]()))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting number operations" must {
      "correctly map and unmap an NumberAddOperation" in {
        val op = AppliedNumberAddOperation(valueId, noOp = true, 1)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an NumberSetOperation" in {
        val op = AppliedNumberSetOperation(valueId, noOp = true, 1, Some(3))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          AppliedObjectSetOperation(valueId, noOp = true, complexJsonObject.children, Some(Map[String, DataValue]())),
          AppliedArrayRemoveOperation("vid2", noOp = true, 3, Some(StringValue("oldId", "oldValue"))))

        val op = AppliedCompoundOperation(ops)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }
  }
}
