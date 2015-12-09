package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

class TransformationFunctionRegistrySpec extends WordSpec with Matchers {

  val StringInsert = StringInsertOperation(List(), false, 1, "")
  val StringRemove = StringRemoveOperation(List(), false, 1, "")
  val StringSet = StringSetOperation(List(), false, "4")

  val ArrayInsert = ArrayInsertOperation(List(), false, 1, JString("4"))
  val ArrayRemove = ArrayRemoveOperation(List(), false, 1)
  val ArrayReplace = ArrayReplaceOperation(List(), false, 1, JString("4"))
  val ArrayMove = ArrayMoveOperation(List(), false, 1, 1)
  val ArraySet = ArraySetOperation(List(), false, JArray(List(JString("4"))))

  val ObjectAddProperty = ObjectAddPropertyOperation(List(), false, "prop", JString("4"))
  val ObjectSetProperty = ObjectSetPropertyOperation(List(), false, "prop", JString("4"))
  val ObjectRemoveProperty = ObjectRemovePropertyOperation(List(), false, "prop")
  val ObjectSet = ObjectSetOperation(List(), false, JObject())

  val NumberAdd = NumberAddOperation(List(), false, JInt(1))
  val NumberSet = NumberSetOperation(List(), false, JInt(1))

  "A TransformationFunctionRegistry" when {

    ///////////////////////////////////////////////////////////////////////////
    // String Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for a StringInsertOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringInsertOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringInsert, StringInsert)
        tf.value shouldBe StringInsertInsertTF
      }

      "return the StringInsertInsertTF when a StringInsertOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringInsert, StringRemove)
        tf.value shouldBe StringInsertRemoveTF
      }

      "return the StringInsertInsertTF when a StringInsertOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringInsert, StringSet)
        tf.value shouldBe StringInsertSetTF
      }
    }

    "getting a TransformationFunction for a StringRemoveOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringRemoveOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringRemove, StringInsert)
        tf.value shouldBe StringRemoveInsertTF
      }

      "return the StringRemoveRemoveTF when a StringRemoveOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringRemove, StringRemove)
        tf.value shouldBe StringRemoveRemoveTF
      }

      "return the StringRemoveRemoveTF when a StringRemoveOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringRemove, StringSet)
        tf.value shouldBe StringRemoveSetTF
      }
    }

    "getting a TransformationFunction for a StringSetOperation and anoter StringOperation" must {
      "return the StringInsertInsertTF when a StringSetOperation and a StringInsertOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringSet, StringInsert)
        tf.value shouldBe StringSetInsertTF
      }

      "return the StringSetSetTF when a StringSetOperation and a StringRemoveOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringSet, StringRemove)
        tf.value shouldBe StringSetRemoveTF
      }

      "return the StringSetSetTF when a StringSetOperation and a StringSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringSet, StringSet)
        tf.value shouldBe StringSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Array Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an ArrayInsertOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayInsertOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayInsert, ArrayInsert)
        tf.value shouldBe ArrayInsertInsertTF
      }

      "return the ArrayInsertRemoveTF when an ArrayInsertOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayInsert, ArrayRemove)
        tf.value shouldBe ArrayInsertRemoveTF
      }

      "return the ArrayInsertReplaceTF when an ArrayInsertOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayInsert, ArrayReplace)
        tf.value shouldBe ArrayInsertReplaceTF
      }

      "return the ArrayInsertMoveTF when an ArrayInsertOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayInsert, ArrayMove)
        tf.value shouldBe ArrayInsertMoveTF
      }

      "return the ArrayInsertSetTF when an ArrayInsertOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayInsert, ArraySet)
        tf.value shouldBe ArrayInsertSetTF
      }
    }

    "getting a TransformationFunction for an ArrayRemoveOperation and anoter ArrayOperation" must {
      "return the ArrayRemoveInsertTF when an ArrayRemoveOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayRemove, ArrayInsert)
        tf.value shouldBe ArrayRemoveInsertTF
      }

      "return the ArrayRemoveRemoveTF when an ArrayRemoveOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayRemove, ArrayRemove)
        tf.value shouldBe ArrayRemoveRemoveTF
      }

      "return the ArrayRemoveReplaceTF when an ArrayRemoveOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayRemove, ArrayReplace)
        tf.value shouldBe ArrayRemoveReplaceTF
      }

      "return the ArrayRemoveMoveTF when an ArrayRemoveOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayRemove, ArrayMove)
        tf.value shouldBe ArrayRemoveMoveTF
      }

      "return the ArrayRemoveSetTF when an ArrayRemoveOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayRemove, ArraySet)
        tf.value shouldBe ArrayRemoveSetTF
      }
    }

    "getting a TransformationFunction for an ArrayReplaceOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayReplaceOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayReplace, ArrayInsert)
        tf.value shouldBe ArrayReplaceInsertTF
      }

      "return the ArrayReplaceRemoveTF when an ArrayReplaceOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayReplace, ArrayRemove)
        tf.value shouldBe ArrayReplaceRemoveTF
      }

      "return the ArrayReplaceReplaceTF when an ArrayReplaceOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayReplace, ArrayReplace)
        tf.value shouldBe ArrayReplaceReplaceTF
      }

      "return the ArrayReplaceMoveTF when an ArrayReplaceOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayReplace, ArrayMove)
        tf.value shouldBe ArrayReplaceMoveTF
      }

      "return the ArrayReplaceSetTF when an ArrayReplaceOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayReplace, ArraySet)
        tf.value shouldBe ArrayReplaceSetTF
      }
    }

    "getting a TransformationFunction for an ArrayMoveOperation and anoter ArrayOperation" must {
      "return the ArrayInsertInsertTF when an ArrayMoveOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayMove, ArrayInsert)
        tf.value shouldBe ArrayMoveInsertTF
      }

      "return the ArrayMoveRemoveTF when an ArrayMoveOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayMove, ArrayRemove)
        tf.value shouldBe ArrayMoveRemoveTF
      }

      "return the ArrayMoveReplaceTF when an ArrayMoveOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayMove, ArrayReplace)
        tf.value shouldBe ArrayMoveReplaceTF
      }

      "return the ArrayMoveMoveTF when an ArrayMoveOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayMove, ArrayMove)
        tf.value shouldBe ArrayMoveMoveTF
      }

      "return the ArrayMoveSetTF when an ArrayMoveOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArrayMove, ArraySet)
        tf.value shouldBe ArrayMoveSetTF
      }
    }

    "getting a TransformationFunction for an ArraySetOperation and anoter ArrayOperation" must {
      "return the ArraySetInsertTF when an ArraySetOperation and an ArrayInsertOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, ArrayInsert)
        tf.value shouldBe ArraySetInsertTF
      }

      "return the ArraySetRemoveTF when an ArraySetOperation and an ArrayRemoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, ArrayRemove)
        tf.value shouldBe ArraySetRemoveTF
      }

      "return the ArraySetReplaceTF when an ArraySetOperation and an ArrayReplaceOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, ArrayReplace)
        tf.value shouldBe ArraySetReplaceTF
      }

      "return the ArraySetMoveTF when an ArraySetOperation and an ArrayMoveOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, ArrayMove)
        tf.value shouldBe ArraySetMoveTF
      }

      "return the ArraySetSetTF when an ArraySetOperation and an ArraySetOpertion are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, ArraySet)
        tf.value shouldBe ArraySetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Object Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an ObjectSetPropertyOperation and anoter ObjectOperation" must {
      "return the ObjectSetPropertySetPropertyTF when an ObjectSetPropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSetProperty, ObjectSetProperty)
        tf.value shouldBe ObjectSetPropertySetPropertyTF
      }

      "return the ObjectSetPropertyAddPropertyTF when an ObjectSetPropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSetProperty, ObjectAddProperty)
        tf.value shouldBe ObjectSetPropertyAddPropertyTF
      }

      "return the ObjectSetPropertyRemovePropertyTF when an ObjectSetPropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSetProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectSetPropertyRemovePropertyTF
      }

      "return the ObjectSetPropertyRemovePropertyTF when an ObjectSetPropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSetProperty, ObjectSet)
        tf.value shouldBe ObjectSetPropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectAddPropertyOperation and anoter ObjectOperation" must {
      "return the ObjectAddPropertySetPropertyTF when an ObjectAddPropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectAddProperty, ObjectSetProperty)
        tf.value shouldBe ObjectAddPropertySetPropertyTF
      }

      "return the ObjectAddPropertyAddPropertyTF when an ObjectAddPropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectAddProperty, ObjectAddProperty)
        tf.value shouldBe ObjectAddPropertyAddPropertyTF
      }

      "return the ObjectAddPropertyRemovePropertyTF when an ObjectAddPropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectAddProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectAddPropertyRemovePropertyTF
      }

      "return the ObjectAddPropertyRemovePropertyTF when an ObjectAddPropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectAddProperty, ObjectSet)
        tf.value shouldBe ObjectAddPropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectRemovePropertyOperation and anoter ObjectOperation" must {
      "return the ObjectRemovePropertySetPropertyTF when an ObjectRemovePropertyOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectRemoveProperty, ObjectSetProperty)
        tf.value shouldBe ObjectRemovePropertySetPropertyTF
      }

      "return the ObjectRemovePropertyAddPropertyTF when an ObjectRemovePropertyOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectRemoveProperty, ObjectAddProperty)
        tf.value shouldBe ObjectRemovePropertyAddPropertyTF
      }

      "return the ObjectRemovePropertyRemovePropertyTF when an ObjectRemovePropertyOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectRemoveProperty, ObjectRemoveProperty)
        tf.value shouldBe ObjectRemovePropertyRemovePropertyTF
      }

      "return the ObjectRemovePropertyRemovePropertyTF when an ObjectRemovePropertyOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectRemoveProperty, ObjectSet)
        tf.value shouldBe ObjectRemovePropertySetTF
      }
    }

    "getting a TransformationFunction for an ObjectSetOperation and anoter ObjectOperation" must {
      "return the ObjectSetSetPropertyTF when an ObjectSetOperation and an ObjectSetPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSet, ObjectSetProperty)
        tf.value shouldBe ObjectSetSetPropertyTF
      }

      "return the ObjectSetSetTF when an ObjectSetOperation and an ObjectAddPropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSet, ObjectAddProperty)
        tf.value shouldBe ObjectSetAddPropertyTF
      }

      "return the ObjectSetRemovePropertyTF when an ObjectSetOperation and an ObjectRemovePropertyOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSet, ObjectRemoveProperty)
        tf.value shouldBe ObjectSetRemovePropertyTF
      }

      "return the ObjectSetRemovePropertyTF when an ObjectSetOperation and an ObjectSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSet, ObjectSet)
        tf.value shouldBe ObjectSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Number Operations
    ///////////////////////////////////////////////////////////////////////////

    "getting a TransformationFunction for an NumberAddOperation and anoter NumberOperation" must {
      "return the NumberAddAddTF when a NumberAddOperation and a NumberAddOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(NumberAdd, NumberAdd)
        tf.value shouldBe NumberAddAddTF
      }

      "return the NumberAddSetTF when a NumberAddOperation and a NumberSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(NumberAdd, NumberSet)
        tf.value shouldBe NumberAddSetTF
      }
    }

    "getting a TransformationFunction for an NumberSetOperation and anoter NumberOperation" must {
      "return the NumberSetAddTF when a NumberSetOperation and a NumberAddOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(NumberSet, NumberAdd)
        tf.value shouldBe NumberSetAddTF
      }

      "return the NumberSetSetTF when a NumberSetOperation and a NumberSetOperation are passed in" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(NumberSet, NumberSet)
        tf.value shouldBe NumberSetSetTF
      }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Exceptional Cases
    ///////////////////////////////////////////////////////////////////////////
    "getting a TransformationFunction for an invalid pair of operations" must {
      "return None when a StringOperation is transformed with a non StringOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(StringSet, NumberAdd)
        tf shouldBe None
      }

      "return None when a ArrayOperation is transformed with a non ArrayOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ArraySet, NumberAdd)
        tf shouldBe None
      }

      "return None when a ObjectOperation is transformed with a non ObjectOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(ObjectSet, NumberAdd)
        tf shouldBe None
      }

      "return None when a NumberOperation is transformed with a non NumberOperation" in {
        val tfr = new TransformationFunctionRegistry()
        val tf = tfr.getTransformationFunction(NumberSet, StringSet)
        tf shouldBe None
      }
    }

    "getting a PathTransformationFunction" must {
      "return the correct function for a valid operation" in {
        val tfr = new TransformationFunctionRegistry()
        tfr.getPathTransformationFunction(ArrayInsert).value shouldBe ArrayInsertPTF
        tfr.getPathTransformationFunction(ArrayRemove).value shouldBe ArrayRemovePTF
        tfr.getPathTransformationFunction(ArrayReplace).value shouldBe ArrayReplacePTF
        tfr.getPathTransformationFunction(ArrayMove).value shouldBe ArrayMovePTF
        tfr.getPathTransformationFunction(ArraySet).value shouldBe ArraySetPTF

        tfr.getPathTransformationFunction(ObjectSetProperty).value shouldBe ObjectSetPropertyPTF
        tfr.getPathTransformationFunction(ObjectRemoveProperty).value shouldBe ObjectRemovePropertyPTF
        tfr.getPathTransformationFunction(ObjectSet).value shouldBe ObjectSetPTF
      }

      "return None for a invalid operation" in {
        val tfr = new TransformationFunctionRegistry()

        tfr.getPathTransformationFunction(ObjectAddProperty) shouldBe None

        tfr.getPathTransformationFunction(StringInsert) shouldBe None
        tfr.getPathTransformationFunction(StringRemove) shouldBe None
        tfr.getPathTransformationFunction(StringSet) shouldBe None

        tfr.getPathTransformationFunction(NumberAdd) shouldBe None
        tfr.getPathTransformationFunction(NumberSet) shouldBe None
      }
    }
  }

  "A RegistryKey" when {
    "creating a RegistryKey using of" must {
      "create the proper instnace" in {
        RegistryKey.of[StringInsertOperation, StringRemoveOperation] shouldBe
          RegistryKey(classOf[StringInsertOperation], classOf[StringRemoveOperation])
      }
    }
  }

  "A TFMap" when {
    "registering a transfomration function" must {
      "return a registered function" in {
        val tfMap = new TFMap()
        tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        tfMap.getOperationTransformationFunction(StringInsert, StringRemove).value shouldBe StringInsertRemoveTF
      }

      "return None for a not registered function" in {
        val tfMap = new TFMap()
        tfMap.getOperationTransformationFunction(StringInsert, StringRemove) shouldBe None
      }

      "disallow a duplicate registration" in {
        val tfMap = new TFMap()
        tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        intercept[IllegalArgumentException] {
          tfMap.register[StringInsertOperation, StringRemoveOperation](StringInsertRemoveTF)
        }
      }
    }
  }
}
