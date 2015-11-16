package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }

import scala.collection.JavaConverters._

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

object ConfigurationStore {
  val ConnectionConfig = "connection"
  val RestConfig = "rest"
  val DomainConfig = "domainConfig"
}

class ConfigurationStore(dbPool: OPartitionedDatabasePool) extends Logging {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  private[datastore] def getConfiguration(configKey: String): Option[JValue] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM configuration WHERE configKey = :configKey")
    val params = Map("configKey" -> configKey)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    QueryUtil.flatMapSingleResult(result) { doc =>
      // FIXME seems like we can avoid this.
      val config = parse(doc.toJSON())
      config match {
        case configObject: JObject => {
          val config = configObject \ "config"
          Some(config)
        }
        case _ => None
      }
    }
  }

  private[datastore] def setConfiguration(configKey: String, configuration: JValue) = {
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