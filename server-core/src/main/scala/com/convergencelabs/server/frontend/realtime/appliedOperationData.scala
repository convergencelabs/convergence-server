package com.convergencelabs.server.frontend.realtime
//
//import java.time.Instant
//
//import com.convergencelabs.server.domain.model.data.DataValue
//
//
//case class ModelOperationData(m: String, v: Long, p: Long, u: String, s: String, o: AppliedOperationData)
//
//sealed trait AppliedOperationData
//
//case class AppliedCompoundOperationData(o: List[AppliedDiscreteOperationData]) extends AppliedOperationData
//
//sealed trait AppliedDiscreteOperationData extends AppliedOperationData {
//  def d: String
//  def n: Boolean
//}
//
//// TODO: fix misspelling in operation
//sealed trait AppliedStringOperaitonData extends AppliedDiscreteOperationData
//case class AppliedStringInsertOperationData(d: String, n: Boolean, i: Int, v: String) extends AppliedStringOperaitonData
//case class AppliedStringRemoveOperationData(d: String, n: Boolean, i: Int, l: Int, o: Option[String]) extends AppliedStringOperaitonData
//case class AppliedStringSetOperationData(d: String, n: Boolean, v: String, o: Option[String]) extends AppliedStringOperaitonData
//
//sealed trait AppliedArrayOperaitonData extends AppliedDiscreteOperationData
//case class AppliedArrayInsertOperationData(d: String, n: Boolean, i: Int, v: DataValue) extends AppliedArrayOperaitonData
//case class AppliedArrayRemoveOperationData(d: String, n: Boolean, i: Int, o: Option[DataValue]) extends AppliedArrayOperaitonData
//case class AppliedArrayReplaceOperationData(d: String, n: Boolean, i: Int, v: DataValue, o: Option[DataValue]) extends AppliedArrayOperaitonData
//case class AppliedArrayMoveOperationData(d: String, n: Boolean, f: Int, o: Int) extends AppliedArrayOperaitonData
//case class AppliedArraySetOperationData(d: String, n: Boolean, v: List[DataValue], o: Option[List[DataValue]]) extends AppliedArrayOperaitonData
//
//sealed trait AppliedObjectOperaitonData extends AppliedDiscreteOperationData
//case class AppliedObjectAddPropertyOperationData(d: String, n: Boolean, k: String, v: DataValue) extends AppliedObjectOperaitonData
//case class AppliedObjectSetPropertyOperationData(d: String, n: Boolean, k: String, v: DataValue, o: Option[DataValue]) extends AppliedObjectOperaitonData
//case class AppliedObjectRemovePropertyOperationData(d: String, n: Boolean, k: String, o: Option[DataValue]) extends AppliedObjectOperaitonData
//case class AppliedObjectSetOperationData(d: String, n: Boolean, v: Map[String, DataValue], o: Option[Map[String, DataValue]]) extends AppliedObjectOperaitonData
//
//sealed trait AppliedNumberOperaitonData extends AppliedDiscreteOperationData
//case class AppliedNumberAddOperationData(d: String, n: Boolean, v: Double) extends AppliedNumberOperaitonData
//case class AppliedNumberSetOperationData(d: String, n: Boolean, v: Double, o: Option[Double]) extends AppliedNumberOperaitonData
//
//sealed trait AppliedBooleanOperaitonData extends AppliedDiscreteOperationData
//case class AppliedBooleanSetOperationData(d: String, n: Boolean, v: Boolean, o: Option[Boolean]) extends AppliedBooleanOperaitonData
//
//sealed trait AppliedDateOperationData extends AppliedDiscreteOperationData
//case class AppliedDateSetOperationData(d: String, n: Boolean, v: Instant, o: Option[Instant]) extends AppliedDateOperationData
