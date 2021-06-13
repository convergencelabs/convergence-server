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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.activity.ActivityStore
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException, PersistenceStoreSpec}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.NonRecordingSchemaManager
import com.convergencelabs.convergence.server.model.domain.activity.{Activity, ActivityId}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Instant
import java.util.Date

// scalastyle:off magic.number
class ActivityStoreSpec
  extends PersistenceStoreSpec[ActivityStore](NonRecordingSchemaManager.SchemaType.Domain)
    with AnyWordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): ActivityStore = new ActivityStore(dbProvider)

  private val date = Instant.ofEpochMilli(new Date().getTime)

  private val activity1Id = ActivityId("t1", "i1")
  private val activity1 = Activity(activity1Id, privateAccess = true, date)

  private val activity2Id = ActivityId("t1", "i2")
  private val activity2 = Activity(activity2Id, privateAccess = true, date)

  private val activity3Id = ActivityId("t2", "i1")
  private val activity3 = Activity(activity3Id, privateAccess = true, date)

  private val activity4Id = ActivityId("t2", "i2")
  private val activity4 = Activity(activity4Id, privateAccess = true, date)

  "An ActivityStore" when {
    "asked whether an activity exists" must {
      "return false if the activity doesn't exist" in withPersistenceStore { store =>
        store.exists(activity1Id).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.createActivity(activity1)
        store.exists(activity1.id).get shouldBe true
      }
    }

    "creating an activity" must {
      "create a activity that is not a duplicate id" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.getActivity(activity1.id).get shouldBe activity1
      }


      "not create a activity that is a duplicate activity id" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity1).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "getting a activity" must {
      "throw if it doesn't exist" in withPersistenceStore { store =>
        store.getActivity(activity1Id).failed.get shouldBe an[EntityNotFoundException]
      }

      "return the correct activity if it does exist" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.getActivity(activity1.id).get shouldBe activity1
        store.getActivity(activity2.id).get shouldBe activity2
      }
    }

    "finding a activity" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.findActivity(activity1Id).get shouldBe None
      }

      "return the correct activity if it does exist" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.findActivity(activity1.id).get shouldBe Some(activity1)
        store.findActivity(activity2.id).get shouldBe Some(activity2)
      }
    }

    "getting all activities" must {
      "return all activities when no limit or offset are provided" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.createActivity(activity3).get
        store.createActivity(activity4).get

        val list = store.getActivities(QueryOffset(), QueryLimit()).get
        list shouldBe PagedData(List(activity1, activity2, activity3, activity4), 0, 4)
      }

      "return only the limited number of activities when limit provided" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.createActivity(activity3).get
        store.createActivity(activity4).get

        val list = store.getActivities(QueryOffset(), QueryLimit(2)).get
        list shouldBe PagedData(List(activity1, activity2), 0, 4)
      }

      "return activities starting at the correct offset" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.createActivity(activity3).get
        store.createActivity(activity4).get

        val list = store.getActivities(QueryOffset(1), QueryLimit()).get
        list shouldBe PagedData(List(activity2, activity3, activity4), 1, 4)
      }

      "return only the correct activities when a limit and offset are provided" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.createActivity(activity3).get
        store.createActivity(activity4).get

        val list = store.getActivities(QueryOffset(1), QueryLimit(2)).get
        list shouldBe PagedData(List(activity2, activity3), 1, 4)
      }
    }

    "deleting a specific activity" must {
      "delete the specified activity and no others" in withPersistenceStore { store =>
        store.createActivity(activity1).get
        store.createActivity(activity2).get
        store.createActivity(activity3).get
        store.createActivity(activity4).get

        store.getActivity(activity1.id).get
        store.getActivity(activity2.id).get
        store.getActivity(activity3.id).get
        store.getActivity(activity4.id).get

        store.deleteActivity(activity2.id).get

        store.getActivity(activity1.id).get
        store.findActivity(activity2.id).get shouldBe None
        store.getActivity(activity3.id).get
        store.getActivity(activity4.id).get
      }

      "return EntityNotFoundException for deleting a non-existent activity" in withPersistenceStore { store =>
        store.deleteActivity(activity1.id).failed.get shouldBe an[EntityNotFoundException]
      }
    }
  }
}
