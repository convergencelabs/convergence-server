import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.db.{ODatabaseType, OrientDB, OrientDBConfig}
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.executor.OResultSet

import scala.jdk.CollectionConverters._

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

object OrientDBTest extends App {
  private[this] val orientDB: OrientDB = new OrientDB(s"memory:target/orientdb/PersistenceStoreSpec/${getClass.getSimpleName}", OrientDBConfig.defaultConfig)

  val dbName = getClass.getSimpleName

  try {
    orientDB.create(dbName, ODatabaseType.MEMORY)
    val db = orientDB.open(dbName, "admin", "admin")

    println("Creating schema")
    val schema = db.getMetadata.getSchema

    val permissionClass = schema.createClass("Permission")
    val targetClass = schema.createClass("Target")

    permissionClass.createProperty("id", OType.STRING)
    permissionClass.createProperty("target", OType.LINKSET, targetClass)

    targetClass.createProperty("id", OType.STRING)
    targetClass.createProperty("permissions", OType.LINKSET, permissionClass)
    targetClass.createIndex("target.id", INDEX_TYPE.UNIQUE, "id")

    val target1: OElement = db.newElement("Target")
    target1.setProperty("id", "t1")
    target1.save
    println("Done creating schema")


    db.begin()
    addPermissions(db, Set("p1", "p2"), "t1")
    db.commit()
    db.begin()
    addPermissions(db, Set("p3", "p4"), "t1")
    db.commit()
  } finally {
    orientDB.drop(dbName)
  }

  def addPermissions(db: ODatabaseDocument,
                     permissions: Set[String],
                     targetId: String): Unit = {

    println("Adding permissions")
    val index = db.getMetadata.getIndexManager.getIndex("target.id")
    val doc = index.get(targetId).asInstanceOf[OIdentifiable]
    val targetRid = doc.getIdentity

    val permissionRids = permissions.map { permission =>
      val doc: ODocument = db.newInstance("Permission")
      doc.setProperty("permission", permission)
      doc.setProperty("target", targetRid)
      db.save(doc)
      doc.getIdentity
    }

    val command = s"UPDATE :target SET permissions = permissions || :permissions"
    val params = Map("target" -> targetRid, "permissions" -> permissionRids.asJava)

    val results: OResultSet = db.command(command, params.asJava)
    val result = results.next()
    val count = result.getProperty("count").asInstanceOf[Long]
    println("Mutated targets: " + count)

    results.close()
  }
}