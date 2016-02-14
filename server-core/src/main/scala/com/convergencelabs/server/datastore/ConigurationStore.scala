package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.mapAsJavaMapConverter
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue
import org.json4s.NoTypeHints
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.JsonMethods.render
import org.json4s.jackson.Serialization
import org.json4s.jvalue2extractable
import org.json4s.jvalue2monadic
import org.json4s.string2JsonInput
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import grizzled.slf4j.Logging
import com.convergencelabs.server.util.JValueMapper
import com.orientechnologies.orient.core.metadata.schema.OType
import scala.util.Try

object ConfigurationStore {
  val ConnectionConfig = "connection"
  val RestConfig = "rest"
  val DomainConfig = "domainConfig"
}

class ConfigurationStore private[datastore] (dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  private[this] def getConfiguration(configKey: String): Try[Option[JValue]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT value FROM configuration WHERE key = :key")
    val params = Map("key" -> configKey)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.mapSingletonListToOption(result) { doc =>
      val value: Object = doc.field("value")
      JValueMapper.javaToJValue(value).toOption
    }
  }

  def getConnectionConfig(): Try[ConnectionConfig] = {
    getConfiguration(ConfigurationStore.ConnectionConfig).map(_.get.extract[ConnectionConfig])
  }
}
