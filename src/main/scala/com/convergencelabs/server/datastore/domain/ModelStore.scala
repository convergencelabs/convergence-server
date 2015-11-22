package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.{ List => JavaList }
import java.util.{ Map => JMap }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try
import org.json4s._
import org.json4s.jackson.JsonMethods.compact
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.JsonMethods.render
import org.json4s.jackson.Serialization
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import org.json4s.JsonAST.JNumber

object ModelStore {
  val CollectionId = "collectionId"
  val ModelId = "modelId"
  val CreatedTime = "createdTime"
  val ModifiedTime = "modifiedTime"
  val Version = "version"
  val Data = "data"

  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder();
    pathBuilder.append(Data);
    path.foreach { p =>
      p match {
        case p: Int => pathBuilder.append("[").append(p).append("]")
        case p: String => pathBuilder.append(".").append(p)
      }
    }
    return pathBuilder.toString();
  }
}

class ModelStore(dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Try[Boolean] = tryWithDb { db =>
    val queryString = 
      "SELECT modelId FROM Model WHERE collectionId = :collectionId AND modelId = :modelId"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    !result.isEmpty()
  }

  def createModel(fqn: ModelFqn, data: JValue, creationTime: Instant): Try[Unit] = tryWithDb { db =>
    val dataObject = JObject(List((ModelStore.Data, data)))
    val doc = db.newInstance("model")
    doc.fromJSON(compact(render(dataObject)))
    doc.field(ModelStore.ModelId, fqn.modelId)
    doc.field(ModelStore.CollectionId, fqn.collectionId)
    doc.field(ModelStore.Version, 0)
    doc.field(ModelStore.CreatedTime, creationTime.toEpochMilli()) // FIXME Update database to use datetime
    doc.field(ModelStore.ModifiedTime, creationTime.toEpochMilli()) // FIXME Update database to use datetime
    db.save(doc)
    Unit
  }

  def deleteModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    db.command(command).execute(params.asJava)
    Unit
  }

  def getModelMetaData(fqn: ModelFqn): Try[Option[ModelMetaData]] = tryWithDb { db =>
    val queryString =
      """SELECT modelId, collectionId, version, created, modified 
        |FROM Model 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result)(docToModelMetaData(_))
  }

  def getModelData(fqn: ModelFqn): Try[Option[ModelData]] = tryWithDb { db =>
    val queryString =
      """SELECT * 
        |FROM Model 
        |WHERE 
        |  collectionId = :collectionId AND 
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result)(docToModelData(_))
  }

  def getModelJsonData(fqn: ModelFqn): Try[Option[JValue]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT data FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingleResult(result)(doc => parse(doc.toJSON()) \\ ModelStore.Data)
  }

  def getModelFieldDataType(fqn: ModelFqn, path: List[Any]): Try[Option[DataType.Value]] = tryWithDb { db =>
    val pathString = ModelStore.toOrientPath(path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT pathString FROM model WHERE collectionId = :collectionId and modelId = :modelId")
    val params = Map("collectionId" -> fqn.collectionId, "modelId" -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    // FIXME I don't think we need to do this, this way. It seems like the ODoc
    // would have a field that we could just check the type of?
    // Also this could be fairly expensive.  imagine we are adding a property to
    // the root level object and this is a big document.  We basically have
    // to query the whole damn thing, just to figure out the type?
    QueryUtil.mapSingleResult(result)(doc => {
      (parse(doc.toJSON()) \\ ModelStore.Data) match {
        case data: JObject => DataType.OBJECT
        case data: JArray => DataType.ARRAY
        case data: JString => DataType.STRING
        case data: JNumber => DataType.NUMBER
        case data: JBool => DataType.BOOLEAN
        case _ => DataType.NULL
      }
    })
  }

  def getAllModels(
    orderBy: String,
    ascending: Boolean,
    offset: Int,
    limit: Int): Try[List[ModelMetaData]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model")
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { doc => docToModelMetaData(doc) }
  }

  def getAllModelsInCollection(
    collectionId: String,
    orderBy: String,
    ascending: Boolean,
    offset: Int,
    limit: Int): Try[List[ModelMetaData]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT modelId, collectionId, version, created, modified FROM model where collectionId = :collectionId")
    val params = Map("collectionid" -> collectionId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { doc => docToModelMetaData(doc) }
  }

  def docToModelData(doc: ODocument): ModelData = {
    val modelData: JMap[String, Any] = doc.field("data", OType.EMBEDDEDMAP)
    ModelData(
      ModelMetaData(
        ModelFqn(doc.field("modelId"), doc.field("collectionId")),
        doc.field("version", OType.LONG),
        Instant.ofEpochMilli(doc.field("creationTime", OType.LONG)), // FIXME make a date in the DB
        Instant.ofEpochMilli(doc.field("modifiedTime", OType.LONG))), // FIXME make a date in the DB
      JValueMapper.javaToJValue(modelData))
  }

  def docToModelMetaData(doc: ODocument): ModelMetaData = {
    ModelMetaData(
      ModelFqn(doc.field("modelId"), doc.field("collectionId")),
      doc.field("version", OType.LONG),
      Instant.ofEpochMilli(doc.field("creationTime", OType.LONG)), // FIXME make a date in the DB
      Instant.ofEpochMilli(doc.field("modifiedTime", OType.LONG))) // FIXME make a date in the DB
  }
}

// FIXME review these names
case class ModelData(metaData: ModelMetaData, data: JValue)
case class ModelMetaData(fqn: ModelFqn, version: Long, createdTime: Instant, modifiedTime: Instant)

object DataType extends Enumeration {
  val ARRAY, OBJECT, STRING, NUMBER, BOOLEAN, NULL = Value
}
