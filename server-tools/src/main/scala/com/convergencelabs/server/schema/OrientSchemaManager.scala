package com.convergencelabs.server.schema

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.schema.OrientSchemaManager.Fields
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.json4s.ShortTypeHints
import java.io.File
import org.json4s.jackson.JsonMethods
import org.json4s.Extraction
import com.orientechnologies.orient.core.metadata.schema.OClass

object DBType extends Enumeration {
  val Convergence, Domain = Value
}

object OrientSchemaManager {
  val DatabaseVersion = "DatabaseVersion"
  val ManagerVersion = 1

  object Fields {
    val Version = "version"
    val ManagerVersion = "managerVersion"
  }
}

class OrientSchemaManager(db: ODatabaseDocumentTx, rootDeltaDir: String, dbType: DBType.Value) {

  private[this] val mapper = new ObjectMapper(new YAMLFactory())
  private implicit val format = DefaultFormats.withTypeHintFieldName("type") +
    ShortTypeHints(List(classOf[CreateClass], classOf[AlterClass], classOf[DropClass],
      classOf[AddProperty], classOf[AlterProperty], classOf[DropProperty],
      classOf[CreateIndex], classOf[DropIndex],
      classOf[CreateSequence], classOf[DropSequence],
      classOf[RunSQLCommand],
      classOf[CreateFunction], classOf[AlterFunction], classOf[DropFunction])) +
    new EnumNameSerializer(OrientType) +
    new EnumNameSerializer(IndexType) +
    new EnumNameSerializer(SequenceType)

  private[this] var currentVersion: Option[Int] = None
  private[this] var currentManagerVersion: Option[Int] = None

  private[this] val processor = new OrientSchemaProcessor(db)
  private[this] val versionDeltaDirectory = s"${rootDeltaDir}/version"
  private[this] val deltaDirectory =
    s"${rootDeltaDir}/${
      dbType match {
        case DBType.Convergence => "convergence"
        case DBType.Domain      => "domain"
      }
    }"

  def currentVersion(): Int = {
    currentVersion = currentVersion.orElse({
      val oClass: Option[OClass] = Option(db.getMetadata().getSchema.getClass(OrientSchemaManager.DatabaseVersion))
      currentVersion = oClass.map { _.getCustom(Fields.Version).toInt }
      currentVersion
    })
    currentVersion.getOrElse(0)
  }

  private[this] def currentManagerVersion(): Int = {
    currentManagerVersion = currentManagerVersion.orElse({
      val oClass: Option[OClass] = Option(db.getMetadata().getSchema.getClass(OrientSchemaManager.DatabaseVersion))
      currentManagerVersion = oClass.map { _.getCustom(Fields.ManagerVersion).toInt }
      currentManagerVersion
    })
    currentManagerVersion.getOrElse(0)
  }

  def upgradeToVersion(version: Int): Unit = {
    upgradeManagerVersion()
    val startVersion = currentVersion() + 1
    val deltas = for (i <- startVersion to version) yield getDelta(i, deltaDirectory)
    deltas.foreach {
      delta => processor.applyDelta(delta)
    }
    db.getMetadata.getSchema.getClass(OrientSchemaManager.DatabaseVersion).setCustom(Fields.Version, version.toString)
  }

  private[this] def upgradeManagerVersion() {
    val startVersion = currentManagerVersion() + 1
    val deltas = for (i <- startVersion to OrientSchemaManager.ManagerVersion) yield getDelta(i, versionDeltaDirectory)
    deltas.foreach { delta =>
      processor.applyDelta(delta)
    }
  }

  private[this] def getDelta(version: Int, deltaDir: String): Delta = {
    val jsonNode = mapper.readTree(new File(s"${deltaDir}/${version}.yaml"))
    val jValue = JsonMethods.fromJsonNode(jsonNode)
    println(jsonNode)
    Extraction.extract[Delta](jValue)
  }
}