package com.convergencelabs.server.datastore.convergence

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.convergence.schema.ConfigClass
import com.convergencelabs.server.datastore.convergence.schema.ConfigClass.Fields
import com.convergencelabs.server.db.DatabaseProvider
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.record.impl.ODocument

import grizzled.slf4j.Logging

object ConfigStore {

  object Params {
    val Key = "key"
    val Keys = "keys"
    val Value = "value"
  }

  def configToDoc(config: (String, Any), db: ODatabaseDocument): Try[ODocument] = Try {
    val (key, value) = config
    val doc = db.newInstance(ConfigClass.ClassName).asInstanceOf[ODocument]
    doc.setProperty(Fields.Key, key)
    doc.setProperty(Fields.Value, value)
    doc
  }

  def docToConfig(doc: ODocument): (String, Any) = {
    (doc.getProperty(Fields.Key), doc.getProperty(Fields.Value).asInstanceOf[Any])
  }
}

class ConfigStore(dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  import ConfigStore._

  def setConfig(key: String, value: Any): Try[Unit] = withDb { db =>
    val command = "UPDATE Config SET value = :value UPSERT WHERE key = :key"
    val params = Map(Params.Key -> key, Params.Value -> value)
    OrientDBUtil.command(db, command, params).map(_ => ())
  }

  def setConfigs(configs: Map[String, Any]): Try[Unit] = {
    Try {
      configs.foreach {
        case (key, value) => setConfig(key, value).get
      }
    }
  }

  def getConfig(key: String): Try[Option[Any]] = {
    getConfigs(List(key)).map(_.get(key))
  }

  def getConfigs(keys: List[String]): Try[Map[String, Any]] = withDb { db =>
    val params = Map(Params.Keys -> keys.asJava)
    OrientDBUtil.query(db, "SELECT FROM Config WHERE key IN :keys", params)
      .map(_.map(ConfigStore.docToConfig(_)))
      .map(_.toMap)
  }
  
  def getConfigsByFilter(filters: List[String]): Try[Map[String, Any]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]()
    var paramNo = 0;
    val where = "WHERE " + filters.map { filter => 
      params += (paramNo.toString -> filter.replace("*", "%").toLowerCase)
      val like = s" LIKE :p${paramNo}"
      paramNo = paramNo + 1
      like
    }.mkString(" OR ")
    val query = "SELECT FROM Config " + where
    OrientDBUtil.query(db, query, params.toMap)
      .map(_.map(ConfigStore.docToConfig(_)))
      .map(_.toMap)
  }

  def getConfigs(): Try[Map[String, Any]] = withDb { db =>
    OrientDBUtil.query(db, "SELECT FROM Config")
      .map(_.map(ConfigStore.docToConfig(_)))
      .map(_.toMap)
  }

  def removeConfig(key: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM Config WHERE key = :key"
    val params = Map(Params.Key -> key)
    OrientDBUtil.mutateOneDocument(db, command, params).map(_ => ())
  }
}
