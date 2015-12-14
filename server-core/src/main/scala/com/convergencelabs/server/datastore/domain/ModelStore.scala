package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue
import org.json4s.JsonAST.JNumber
import org.json4s.NoTypeHints
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.json4s.jvalue2monadic
import org.json4s.string2JsonInput

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.domain.mapper.ModelMapper.ModelToODocument
import com.convergencelabs.server.datastore.domain.mapper.ModelMapper.ODocumentToModel
import com.convergencelabs.server.datastore.domain.mapper.ModelMapper.ODocumentToModelMetaData
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

object ModelStore {

  private val Data = "data"
  private val CollectionId = "collectionId"
  private val ModelId = "modelId"
  private val Version = "version"

  private[domain] def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder();
    pathBuilder.append(Data);
    path.foreach { p =>
      p match {
        case p: Int => pathBuilder.append("[").append(p).append("]")
        case p: String => pathBuilder.append(".").append(p)
      }
    }
    pathBuilder.toString();
  }
}

class ModelStore private[domain] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def modelExists(fqn: ModelFqn): Try[Boolean] = tryWithDb { db =>
    val queryString =
      "SELECT modelId FROM Model WHERE collectionId = :collectionId AND modelId = :modelId"
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(
      ModelStore.CollectionId -> fqn.collectionId,
      ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    !result.isEmpty()
  }

  def createModel(model: Model): Try[Unit] = tryWithDb { db =>
    db.save(model.asODocument)
    Unit
  }

  def deleteModel(fqn: ModelFqn): Try[Unit] = tryWithDb { db =>
    val queryString =
      "DELETE FROM Model WHERE collectionId = :collectionId AND modelId = :modelId"
    val command = new OCommandSQL(queryString)
    val params = Map(
      ModelStore.CollectionId -> fqn.collectionId,
      ModelStore.ModelId -> fqn.modelId)
    db.command(command).execute(params.asJava)
    Unit
  }

  def getModel(fqn: ModelFqn): Try[Option[Model]] = tryWithDb { db =>
    val queryString =
      """SELECT *
        |FROM Model
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asModel }
  }

  def getModelMetaData(fqn: ModelFqn): Try[Option[ModelMetaData]] = tryWithDb { db =>
    val queryString =
      """SELECT modelId, collectionId, version, created, modified
        |FROM Model
        |WHERE
        |  collectionId = :collectionId AND
        |  modelId = :modelId""".stripMargin
    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result) { _.asModelMetaData }
  }

  def getAllModelMetaData(
    orderBy: String,
    ascending: Boolean,
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString = QueryUtil.buildPagedQuery(
      "SELECT modelId, collectionId, version, created, modified FROM Model",
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](queryString)
    val result: JavaList[ODocument] = db.command(query).execute()
    result.asScala.toList map { _.asModelMetaData }
  }

  def getAllModelMetaDataInCollection(
    collectionId: String,
    orderBy: String,
    ascending: Boolean,
    offset: Option[Int],
    limit: Option[Int]): Try[List[ModelMetaData]] = tryWithDb { db =>

    val queryString = QueryUtil.buildPagedQuery(
      "SELECT modelId, collectionId, version, created, modified FROM Model WHERE collectionId = :collectionId",
      limit,
      offset)

    val query = new OSQLSynchQuery[ODocument](queryString)
    val params = Map("collectionid" -> collectionId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList map { _.asModelMetaData }
  }

  def getModelData(fqn: ModelFqn): Try[Option[JValue]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT data FROM Model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(result)(doc => parse(doc.toJSON()) \\ ModelStore.Data)
  }

  def getModelFieldDataType(fqn: ModelFqn, path: List[Any]): Try[Option[DataType.Value]] = tryWithDb { db =>
    val pathString = ModelStore.toOrientPath(path)
    val query = new OSQLSynchQuery[ODocument](s"SELECT $pathString FROM Model WHERE collectionId = :collectionId AND modelId = :modelId")
    val params = Map(ModelStore.CollectionId -> fqn.collectionId, ModelStore.ModelId -> fqn.modelId)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    // FIXME I don't think we need to do this, this way. It seems like the ODoc
    // would have a field that we could just check the type of?
    // Also this could be fairly expensive.  imagine we are adding a property to
    // the root level object and this is a big document.  We basically have
    // to query the whole damn thing, just to figure out the type?
    QueryUtil.mapSingletonList(result)(doc => {
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
}

object DataType extends Enumeration {
  val ARRAY, OBJECT, STRING, NUMBER, BOOLEAN, NULL = Value
}
