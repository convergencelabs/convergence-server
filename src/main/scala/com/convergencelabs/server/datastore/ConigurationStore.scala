package com.convergencelabs.server.datastore

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JObject
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.collection.immutable.HashMap
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.Map
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{ read, write }

object ConfigurationStore {
  val ConnectionConfig = "connection"
  val RestConfig = "rest"
}

class ConfigurationStore(dbPool: OPartitionedDatabasePool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  def getConfiguration(configKey: String): Option[JValue] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM configuration WHERE configKey = :configKey")
    val params: java.util.Map[String, String] = HashMap("configKey" -> configKey)
    val result: java.util.List[ODocument] = db.command(query).execute(params)
    db.close()
    result.asScala.toList match {
      case doc :: rest => {
        val config = parse(doc.toJSON())
        config match {
          case configObject: JObject => {
            val config = configObject \ "config"
            Some(config)
          }
          case _ => None
        }
      }
      case Nil => None
    }
  }

  def setConfiguration(configKey: String, configuration: JValue) = {
    val db = dbPool.acquire()
    val document = new ODocument();
    document.field("configKey", configKey);
    document.field("config", render(configuration));
    db.save(document, "configuration");
  }

  def getConnectionConfig(): Option[ConnectionConfig] = {
    getConfiguration(ConfigurationStore.ConnectionConfig) map (_.extract[ConnectionConfig])
  }

  def getRestConfig(): Option[RestConfig] = {
    getConfiguration(ConfigurationStore.RestConfig) map (_.extract[RestConfig])
  }
}
