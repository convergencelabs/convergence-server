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
import com.convergencelabs.convergence.server.api.realtime.protocol.CommonProtoConverters.{instantToTimestamp, timestampToInstant}
import com.convergencelabs.convergence.server.model.domain.model._

/**
 * A helper utility to convert Model DataValues to and from the Protocol
 * Buffers representations.
 */
private[realtime] object DataValueProtoConverters {

  /**
   * Signifies that the Protocol Buffer representation of the DataValue
   * was not valid.
   */
  final case class InvalidDataValue(value: ProtoDataValue)

  //
  // Protocol Buffers => Data Value
  //

  def protoToDataValue(dataValue: ProtoDataValue): Either[InvalidDataValue.type, DataValue] =
    dataValue.value match {
      case ProtoDataValue.Value.ObjectValue(value) =>
        protoToObjectValue(value)
      case ProtoDataValue.Value.ArrayValue(value) =>
        protoToArrayValue(value)
      case ProtoDataValue.Value.BooleanValue(value) =>
        protoToBooleanValue(value)
      case ProtoDataValue.Value.DoubleValue(value) =>
        protoToDoubleValue(value)
      case ProtoDataValue.Value.NullValue(value) =>
        protoToNullValue(value)
      case ProtoDataValue.Value.StringValue(value) =>
        protoToStringValue(value)
      case ProtoDataValue.Value.DateValue(value) =>
        protoToDateValue(value)
      case ProtoDataValue.Value.Empty =>
        Left(InvalidDataValue)
    }

  def protoToObjectValue(objectValue: ProtoObjectValue): Either[InvalidDataValue.type, ObjectValue] =
    protoMapToDataValueMap(objectValue.children).map(ObjectValue(objectValue.id, _))

  def protoMapToDataValueMap(values: Map[String, ProtoDataValue]): Either[InvalidDataValue.type, Map[String, DataValue]] = {
    values.map { case (key, value) =>
      protoToDataValue(value).map((key, _))
    }.partitionMap(scala.Predef.identity) match {
      case (Nil, children) =>
        Right(children.toMap)
      case (_, _) =>
        Left(InvalidDataValue)
    }
  }

  def protoToArrayValue(arrayValue: ProtoArrayValue): Either[InvalidDataValue.type, ArrayValue] =
    protoSeqToDataValueList(arrayValue.children).map(ArrayValue(arrayValue.id, _))

  def protoSeqToDataValueList(values: Seq[ProtoDataValue]): Either[InvalidDataValue.type, List[DataValue]] = {
    values.toList.map(protoToDataValue).partitionMap(scala.Predef.identity) match {
      case (Nil, children) =>
        Right(children)
      case (_, _) =>
        Left(InvalidDataValue)
    }
  }

  def protoToBooleanValue(booleanValue: ProtoBooleanValue): Either[InvalidDataValue.type, BooleanValue] =
    Right(BooleanValue(booleanValue.id, booleanValue.value))

  def protoToDoubleValue(doubleValue: ProtoDoubleValue): Either[InvalidDataValue.type, DoubleValue]  =
    Right(DoubleValue(doubleValue.id, doubleValue.value))

  def protoToNullValue(nullValue: ProtoNullValue): Either[InvalidDataValue.type, NullValue]  =
    Right(NullValue(nullValue.id))

  def protoToStringValue(stringValue: ProtoStringValue): Either[InvalidDataValue.type, StringValue]  =
    Right(StringValue(stringValue.id, stringValue.value))

  def protoToDateValue(dateValue: ProtoDateValue): Either[InvalidDataValue.type, DateValue]  =
    Right(DateValue(dateValue.id, timestampToInstant(dateValue.value.get)))

  //
  // Data Value => Protocol Buffers
  //

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

  def arrayValueToProto(arrayValue: ArrayValue): ProtoArrayValue =
    ProtoArrayValue(arrayValue.id, arrayValue.children.map(dataValueToProto))

  def booleanValueToProto(booleanValue: BooleanValue): ProtoBooleanValue =
    ProtoBooleanValue(booleanValue.id, booleanValue.value)

  def doubleValueToProto(doubleValue: DoubleValue): ProtoDoubleValue =
    ProtoDoubleValue(doubleValue.id, doubleValue.value)

  def nullValueToProto(nullValue: NullValue): ProtoNullValue =
    ProtoNullValue(nullValue.id)

  def stringValueToProto(stringValue: StringValue): ProtoStringValue =
    ProtoStringValue(stringValue.id, stringValue.value)

  def dateValueToProto(dateValue: DateValue): ProtoDateValue =
    ProtoDateValue(dateValue.id, Some(instantToTimestamp(dateValue.value)))
}
