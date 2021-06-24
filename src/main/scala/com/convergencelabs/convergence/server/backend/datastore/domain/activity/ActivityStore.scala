/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.activity

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.ActivityClass.{ClassName, Fields, Indices}
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema.Classes
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.activity.{Activity, ActivityId}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import java.time.Instant
import java.util
import java.util.Date
import scala.util.{Failure, Try}

/**
 * The ActivityStore manages Activity instances in the database.
 *
 * @param dbProvider The DatabaseProvider that provides a connection to
 *                   the database.
 */
class ActivityStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
    with Logging {

  import ActivityStore._

  /**
   * Gets all activities in the system in pages of results.
   *
   * @param offset The index of the first result in the total ordering.
   * @param limit  The maximum number of results to return.
   *
   * @return A page of activities matching the offset and limit.
   */
  def getActivities(limit: QueryLimit, offset: QueryOffset): Try[PagedData[Activity]] = withDb { db =>
    val query = OrientDBUtil.buildPagedQuery(GetActivitiesQuery, limit, offset)
    for {
     activities <- OrientDBUtil.queryAndMap(db, query) (docToActivity)
     totalResults <-  OrientDBUtil.getDocument(db, GetActivityCountQuery)
       .map(_.getProperty("count").asInstanceOf[Long])
    } yield {
      PagedData(activities, offset.getOrZero, totalResults)
    }
  }

  private[this] val GetActivitiesQuery = "SELECT * FROM Activity ORDER BY type, id ASC"
  private[this] val GetActivityCountQuery = "SELECT count(*) as count FROM Activity"

  /**
   * Finds all activities in the system matching optional filters.
   *
   * @param typeFilter A string filter that matches the activity type.
   * @param idFilter A string filter that matches the activity id.
   * @param offset The index of the first result in the total ordering.
   * @param limit  The maximum number of results to return.
   *
   * @return A page of activities matching the offset and limit.
   */
  def searchActivities(typeFilter: Option[String], idFilter: Option[String], limit: QueryLimit, offset: QueryOffset): Try[PagedData[Activity]] = withDb { db =>

    val (where, params) = (typeFilter, idFilter) match {
      case (Some(tf), Some(idf)) =>
        ("WHERE type LIKE :type AND id LIKE :id", Map("type" -> s"%$tf%", "id" -> s"%$idf%"))
      case (Some(tf), None) =>
        ("WHERE type LIKE :type", Map("type" -> s"%$tf%"))
      case (None, Some(idf)) =>
        ("WHERE id LIKE :id", Map("id" -> s"%$idf%"))
      case (None, None) =>
        ("", Map[String, Any]())
    }

    val baseQuery = s"SELECT FROM Activity $where ORDER BY type, id ASC"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)
    val countQuery = s"SELECT count(*) as count FROM Activity $where"

    for {
      activities <- OrientDBUtil.queryAndMap(db, query, params) (docToActivity)
      totalResults <-  OrientDBUtil.getDocument(db, countQuery, params)
        .map(_.getProperty("count").asInstanceOf[Long])
    } yield {
      PagedData(activities, offset.getOrZero, totalResults)
    }
  }

  /**
   * Determines if an Activity with the specified id exists.
   *
   * @param id The id of the Activity to determine the existence of.
   *
   * @return True if the Activity exists, false if it does not, or
   *         a Failure if the query could not be completed.
   */
  def exists(id: ActivityId): Try[Boolean] = withDb { db =>
    val params = Map(Fields.Id -> id.id, Fields.Type -> id.activityType)
    OrientDBUtil
      .query(db, ActivityExistsQuery, params)
      .map(_.nonEmpty)
  }

  private[this] val ActivityExistsQuery = "SELECT id, type FROM Activity WHERE id = :id AND type = :type"

  /**
   * Gets an Activity by id.  An error will be returned if the
   * Activity does not exist.
   *
   * @param id The id of the Activity to get.
   *
   * @return The specified Activity, or a Failure if the Activity
   *         does not exist, or the query fails.
   */
  def getActivity(id: ActivityId): Try[Activity] = withDb { db =>
    val params = Map(Fields.Id -> id.id, Fields.Type -> id.activityType)
    OrientDBUtil
      .getDocument(db, GetActivityQuery, params)
      .map(docToActivity)
  }

  /**
   * Gets an Activity that might not exist by id.
   *
   * @param id The id of the Activity to get.
   *
   * @return Some[Activity] if the Activity exists, None if it does not, or a
   *         Failure if the query fails.
   */
  def findActivity(id: ActivityId): Try[Option[Activity]] = withDb { db =>
    val params = Map(Fields.Id -> id.id, Fields.Type -> id.activityType)
    OrientDBUtil.findDocumentAndMap(db, GetActivityQuery, params)(docToActivity)
  }

  private[this] val GetActivityQuery = "SELECT * FROM Activity WHERE id = :id AND type = :type"

  /**
   * Creates a new activity. If an activity with the same id already exists
   * this method will return a Failure.
   *
   * @param activity The activity to create.
   *
   * @return Success if the Activity was created, or a Failure if it was not.
   */
  def createActivity(activity: Activity): Try[Unit] = tryWithDb { db =>
    val activityDoc = activityToDoc(activity)
    db.save(activityDoc)
    ()
  } recoverWith handleDuplicateValue


  /**
   * Deletes an Activity by id. This method will return a failure if
   * the specified Activity does not exist.
   *
   * @param id The id of the Activity to delete.
   * @return A Success if the Activity was deleted, a Failure otherwise.
   */
  def deleteActivity(id: ActivityId): Try[Unit] = withDb { db =>
    val params = Map(Fields.Id -> id.id, Fields.Type -> id.activityType)
    OrientDBUtil.mutateOneDocument(db, DeleteActivityCommand, params)
  }

  private[this] val DeleteActivityCommand = "DELETE FROM Activity WHERE id = :id and type = :type"

  private[this] def handleDuplicateValue[T]: PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Indices.Type_Id =>
          Failure(DuplicateValueException(s"${Fields.Type}, ${Fields.Id}"))
        case _ =>
          Failure(e)
      }
  }
}

object ActivityStore {

  private[domain] def getActivityRid(activityId: ActivityId, db: ODatabaseDocument): Try[ORID] = {
    val ActivityId(t, i) = activityId
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Activity.Indices.Type_Id, List(t, i))
  }

  def activityToDoc(activity: Activity): ODocument = {
    val doc = new ODocument(ClassName)
    doc.setProperty(Fields.Type, activity.id.activityType)
    doc.setProperty(Fields.Id, activity.id.id)
    doc.setProperty(Fields.Ephemeral, activity.ephemeral)
    doc.setProperty(Fields.Created, new Date(activity.created.toEpochMilli))
    doc.setProperty(Fields.Permissions, new util.ArrayList[Any]())
    doc
  }

  def docToActivity(doc: ODocument): Activity = {
    val createdDate: Date = doc.getProperty(Fields.Created)
    Activity(
      ActivityId(
        doc.getProperty(Fields.Type),
        doc.getProperty(Fields.Id),
      ),
      doc.getProperty(Fields.Ephemeral),
      Instant.ofEpochMilli(createdDate.getTime)
    )
  }
}
