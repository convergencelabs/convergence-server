package com.convergencelabs.server.domain.model.ot

object RangeRelationshipUtil {
  
  def getRangeIndexRelationship(rStart: Int, rEnd: Int, index: Int): RangeIndexRelationship.Value = {
    if (index < rStart) {
      RangeIndexRelationship.Before
    } else if (index > rEnd) {
      RangeIndexRelationship.After
    } else if (index == rStart) {
      RangeIndexRelationship.Start
    } else if (index == rEnd) {
      RangeIndexRelationship.End
    } else {
      RangeIndexRelationship.Within
    }
  }
  
  def getRangeRangeRelationship(sStart: Int, sEnd: Int, cStart: Int, cEnd: Int): RangeRangeRelationship.Value = {
    if (sStart == cStart) {
      if (sEnd == cEnd) {
        RangeRangeRelationship.EqualTo
      } else if (cEnd > sEnd) {
        RangeRangeRelationship.Starts
      } else {
        RangeRangeRelationship.StartedBy
      }
    } else if (sStart > cStart) {
      if (sStart > cEnd) {
        RangeRangeRelationship.PrecededBy
      } else if (cEnd == sEnd) {
        RangeRangeRelationship.Finishes
      } else if (sEnd < cEnd) {
        RangeRangeRelationship.ContainedBy
      } else if (sStart == cEnd) {
        RangeRangeRelationship.MetBy
      } else {
        RangeRangeRelationship.OverlappedBy
      }
    } else { // sStart < cStart
      if (sEnd < cStart) {
        RangeRangeRelationship.Precedes
      } else if (cEnd == sEnd) {
        RangeRangeRelationship.FinishedBy
      } else if (sEnd > cEnd) {
        RangeRangeRelationship.Contains
      } else if (sEnd == cStart) {
        RangeRangeRelationship.Meets
      } else {
        RangeRangeRelationship.Overlaps
      }
    }
  }
}