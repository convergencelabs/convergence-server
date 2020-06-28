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

package com.convergencelabs.convergence.server.api.realtime.protocol
import com.convergencelabs.convergence.proto.model
import com.convergencelabs.convergence.proto.model.{ArrayValue => ProtoArrayValue, BooleanValue => ProtoBooleanValue, DataValue => ProtoDataValue, DateValue => ProtoDateValue, DoubleValue => ProtoDoubleValue, NullValue => ProtoNullValue, ObjectValue => ProtoObjectValue, StringValue => ProtoStringValue}
import com.convergencelabs.convergence.server.api.realtime.protocol.CommonProtoConverters.{instanceToTimestamp, timestampToInstant}
import com.convergencelabs.convergence.server.domain.model.data._

private[realtime] object DataValueConverters {
  def dataValueToProto(dataValue: DataValue): ProtoDataValue =
    dataValue match {
      case value: ObjectValue =>
        ProtoDataValue().withObjectValue(objectValueToProto(value))
      case value: ArrayValue =>
        ProtoDataValue().withArrayValue(arrayValueToProto(value))
      case value: BooleanValue =>
        ProtoDataValue().withBooleanValue(booleanValueToProto(value))
      case value: DoubleValue =>
        ProtoDataValue().withDoubleValue(doubleValueToProto(value))
      case value: NullValue =>
        ProtoDataValue().withNullValue(nullValueToProto(value))
      case value: StringValue =>
        ProtoDataValue().withStringValue(stringValueToProto(value))
      case value: DateValue =>
        ProtoDataValue().withDateValue(dateValueToProto(value))
    }

  def objectValueToProto(objectValue: ObjectValue): model.ObjectValue =
    ProtoObjectValue(
      objectValue.id,
      objectValue.children map {
        case (key, value) => (key, dataValueToProto(value))
      })

  def arrayValueToProto(arrayValue: ArrayValue): model.ArrayValue =
    ProtoArrayValue(arrayValue.id, arrayValue.children.map(dataValueToProto))

  def booleanValueToProto(booleanValue: BooleanValue): model.BooleanValue =
    ProtoBooleanValue(booleanValue.id, booleanValue.value)

  def doubleValueToProto(doubleValue: DoubleValue): model.DoubleValue =
    ProtoDoubleValue(doubleValue.id, doubleValue.value)

  def nullValueToProto(nullValue: NullValue): model.NullValue =
    ProtoNullValue(nullValue.id)

  def stringValueToProto(stringValue: StringValue): model.StringValue =
    ProtoStringValue(stringValue.id, stringValue.value)

  def dateValueToProto(dateValue: DateValue): model.DateValue =
    ProtoDateValue(dateValue.id, Some(instanceToTimestamp(dateValue.value)))

  def protoToDataValue(dataValue: com.convergencelabs.convergence.proto.model.DataValue): DataValue =
    dataValue.value match {
      case ProtoDataValue.Value.ObjectValue(value) => protoToObjectValue(value)
      case ProtoDataValue.Value.ArrayValue(value) => protoToArrayValue(value)
      case ProtoDataValue.Value.BooleanValue(value) => protoToBooleanValue(value)
      case ProtoDataValue.Value.DoubleValue(value) => protoToDoubleValue(value)
      case ProtoDataValue.Value.NullValue(value) => protoToNullValue(value)
      case ProtoDataValue.Value.StringValue(value) => protoToStringValue(value)
      case ProtoDataValue.Value.DateValue(value) => protoToDateValue(value)
      case ProtoDataValue.Value.Empty => ???
    }

  def protoToObjectValue(objectValue: com.convergencelabs.convergence.proto.model.ObjectValue): ObjectValue =
    ObjectValue(
      objectValue.id,
      objectValue.children map {
        case (key, value) => (key, protoToDataValue(value))
      })

  def protoToArrayValue(arrayValue: com.convergencelabs.convergence.proto.model.ArrayValue): ArrayValue =
    ArrayValue(arrayValue.id, arrayValue.children.map(protoToDataValue).toList)

  def protoToBooleanValue(booleanValue: com.convergencelabs.convergence.proto.model.BooleanValue): BooleanValue =
    BooleanValue(booleanValue.id, booleanValue.value)

  def protoToDoubleValue(doubleValue: com.convergencelabs.convergence.proto.model.DoubleValue): DoubleValue =
    DoubleValue(doubleValue.id, doubleValue.value)

  def protoToNullValue(nullValue: com.convergencelabs.convergence.proto.model.NullValue): NullValue =
    NullValue(nullValue.id)

  def protoToStringValue(stringValue: com.convergencelabs.convergence.proto.model.StringValue): StringValue =
    StringValue(stringValue.id, stringValue.value)

  def protoToDateValue(dateValue: com.convergencelabs.convergence.proto.model.DateValue): DateValue =
    DateValue(dateValue.id, timestampToInstant(dateValue.value.get))


}
