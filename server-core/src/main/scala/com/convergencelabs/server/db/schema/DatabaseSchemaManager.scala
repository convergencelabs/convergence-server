package com.convergencelabs.server.db.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.json4s.ShortTypeHints
import java.io.File
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import com.orientechnologies.orient.core.metadata.schema.OClass
import org.json4s.FieldSerializer
import scala.io.Source
import java.io.FileNotFoundException
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import java.io.InputStream
import grizzled.slf4j.Logging
import com.convergencelabs.server.util.SimpleNamePolymorphicSerializer

object DatabaseSchemaManager {
  val DatabaseVersion = "DatabaseVersion"

  object Fields {
    val Version = "version"
    val ManagerVersion = "managerVersion"
  }
}

class DatabaseSchemaManager(
  private[this] val dbPool: OPartitionedDatabasePool,
  private[this] val category: DeltaCategory.Value,
  private[this] val preRelease: Boolean)
    extends Logging {

  private[this] implicit val releaseOnly = true

  

  private[this] val versionController = new DatabaseVersionController(dbPool)
  private[this] val deltaManager = new DeltaManager(None)

  def currentVersion(): Try[Int] = {
    versionController.getVersion()
  }

  private[this] def currentManagerVersion(): Try[Int] = {
    versionController.getManagerVersion()
  }

  def upgradeToVersion(version: Int): Try[Unit] = {
    deltaManager.manifest(category) match {
      case Success(manifest) =>
        if (preRelease && version > manifest.maxPreReleaseVersion()) {
          Failure(new IllegalArgumentException(
            s"Requested version is greater than the maximum pre-release version (${manifest.maxPreReleaseVersion()}): ${version}"))
        } else if (version > manifest.maxReleasedVersion()) {
          Failure(new IllegalArgumentException(
            s"Requested version is greater than the maximum released version (${manifest.maxReleasedVersion}): ${version}"))
        }

        upgrade(manifest, version)
      case Failure(e) =>
        logger.error("Unable to load manifest")
        Failure(e)
    }
  }

  def upgradeToLatest(): Try[Unit] = {
    deltaManager.manifest(category) match {
      case Success(manifest) =>
        val max = preRelease match {
          case true => manifest.maxPreReleaseVersion()
          case false => manifest.maxReleasedVersion()
        }
        upgrade(manifest, max)
      case Failure(e) =>
        logger.error("Unable to load manifest")
        Failure(e)
    }
  }

  private[this] def upgrade(manifest: DeltaManifest, version: Int): Try[Unit] = Try {
    upgradeManagerVersion()
    currentVersion() match {
      case Success(currentVersion) =>
        logger.debug("Upgrading database")
//        manifest.forEach(currentVersion, preRelease)((deltaNumber: Int, path: String, in: InputStream) => {
//          getDelta(in) match {
//            case Success(delta) =>
//              logger.debug(s"Applying delta: ${deltaNumber}")
//              this.applyDelta(delta)
//              versionController.setVersion(deltaNumber)
//            case Failure(e) =>
//              logger.error("Unable to apply manager deltas")
//              Failure(e)
//          }
//          Success(())
//        })
        Success(())
      case Failure(e) =>
        logger.error("Unable to lookup current version for database")
        Failure(e)
    }
  }

  private[this] def applyDelta(delta: Delta): Try[Unit] = {
    val db = dbPool.acquire()
    Try {
      val processor = new DatabaseDeltaProcessor(delta, db)
      processor.apply()
      ()
    }.recover {
      case e: Exception =>
        db.close()
        ()
    }
  }

  private[this] def upgradeManagerVersion(): Try[Unit] = {
    currentManagerVersion() match {
      case Success(currentVersion) =>
        if (currentVersion < DatabaseVersionController.ManagerVersion) {
          logger.info(s"Manager schema is out of date.  Upgrading manager schema to version: ${DatabaseVersionController.ManagerVersion}")
          Success(())
//          deltaManager.manifest(DeltaCategory.Version) match {
//            case Success(manifest: DeltaManifest) =>
//              logger.info("Loaded manifest.  Applying deltas...")
//              Try {
//                manifest.forEach(currentVersion, preRelease)((deltaNumber, path, in) => {
//                  getDelta(in) match {
//                    case Success(delta) =>
//                      logger.info(s"Applying delta: $deltaNumber")
//                      this.applyDelta(delta) match {
//                        case Success(()) => versionController.setManagerVersion(deltaNumber)
//                        case Failure(e) =>
//                          logger.error("Unable to apply manager deltas")
//                          Failure(e)
//                      }
//                    case Failure(e) =>
//                      logger.error("Unable to read delta")
//                      Failure(e)
//                  }
//                  Success(())
//                })
//              }
//            case Failure(e) =>
//              logger.error("Unable to load manifest for manager")
//              Failure(e)
//          }
        } else {
          Success(())
        }
      case Failure(e) =>
        logger.error("Unable to lookup current manager version for database")
        Failure(e)
    }
  }

  
}