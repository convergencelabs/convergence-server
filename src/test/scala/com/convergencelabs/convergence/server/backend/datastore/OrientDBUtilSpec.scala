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

package com.convergencelabs.convergence.server.backend.datastore

import com.convergencelabs.convergence.server.util.TryWithResource
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.orientechnologies.orient.core.record.OElement
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// scalastyle:off multiple.string.literals
class OrientDBUtilSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private val ClassName = "TestClass"
  private val Key1 = "key1"

  private val orientDB: OrientDB = new OrientDB("memory:target/orientdb/OrientDBUtilSpec", OrientDBConfig.defaultConfig())
  
  override def afterAll(): Unit = {
    orientDB.close()
  }
  
  "A OrientDBUtil" when {
    
    "executing a query" must {
      "return the correct documents" in withDatabase { db =>  
         val element1: OElement = db.newElement(ClassName)
         element1.setProperty(Key1, "test1")
         element1.save()
         
         val element2: OElement = db.newElement(ClassName)
         element2.setProperty(Key1, "test2")
         element2.save()
         
         val query = s"SELECT FROM $ClassName WHERE $Key1 = 'test1'"
         val docs = OrientDBUtil.query(db, query).get
         
         docs.size shouldBe 1
         val doc = docs.head
         doc.getIdentity shouldBe element1.getIdentity
      }
    }
  }
  
  def withDatabase(testCode: ODatabaseDocument => Any): Any = {
    val dbName = "test-" + System.currentTimeMillis()
    orientDB.create(dbName, ODatabaseType.MEMORY)
    TryWithResource(orientDB.open(dbName,"admin","admin")) { db =>
      testCode(db)
    }.get
  }
}
