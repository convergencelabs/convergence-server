package com.convergencelabs.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import DatabaseVersionController.Fields

object DatabaseVersionController {
  val DatabaseVersion = "DatabaseVersion"
  val ManagerVersion = 1

  object Fields {
    val Version = "version"
    val ManagerVersion = "managerVersion"
  }
}

class DatabaseVersionController(dbPool: OPartitionedDatabasePool) {

  private[this] var version: Option[Int] = None
  private[this] var managerVersion: Option[Int] = None

  def getVersion(): Try[Int] = Try {
    version = version.orElse({
      val db = dbPool.acquire()
      val oClass: Option[OClass] = Option(db.getMetadata().getSchema.getClass(DatabaseVersionController.DatabaseVersion))
      version = oClass.flatMap { c => Option(c.getCustom(Fields.Version)) } map { _.toInt }
      db.close()
      version
    })
    version.getOrElse(0)
  }

  def setVersion(version: Int): Try[Unit] = Try {
    val db = dbPool.acquire()
    db.getMetadata.getSchema.getClass(DatabaseVersionController.DatabaseVersion).setCustom(Fields.Version, version.toString)
    db.close()
  }

  def getManagerVersion(): Try[Int] = Try {

    managerVersion = managerVersion.orElse({
      val db = dbPool.acquire()
      val oClass: Option[OClass] = Option(db.getMetadata().getSchema.getClass(DatabaseVersionController.DatabaseVersion))
      managerVersion = oClass.flatMap { c => Option(c.getCustom(Fields.ManagerVersion)) } map { _.toInt }
      db.close()
      managerVersion
    })

    managerVersion.getOrElse(0)
  }

  def setManagerVersion(version: Int): Try[Unit] = Try {
    val db = dbPool.acquire()
    db.getMetadata.getSchema.getClass(DatabaseVersionController.DatabaseVersion).setCustom(Fields.ManagerVersion, version.toString)
    db.close()
  }
  
  def validManager(): Boolean = {
    DatabaseVersionController.ManagerVersion == getManagerVersion().get
  }
}