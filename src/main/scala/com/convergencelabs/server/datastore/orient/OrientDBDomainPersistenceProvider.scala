package com.convergencelabs.server.datastore.orient

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelHistoryStore
import org.json4s.Extraction
import org.json4s.JsonAST.JValue
import org.json4s.NoTypeHints
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import com.convergencelabs.server.datastore.domain.ModelStore





class OrientDBDomainPersistenceProvider(databaseConfig: JValue) extends DomainPersistenceProvider(databaseConfig) {
  
  case class OrientDBConfig(uri: String, username: String, password: String)
  
  private[this] implicit val formats = Serialization.formats(NoTypeHints)
  
  val config = databaseConfig.extract[OrientDBConfig]
  
  val dbPool: OPartitionedDatabasePool = new OPartitionedDatabasePool(config.uri, config.username, config.password)

  val modelStore: ModelStore = new OrientDBModelStore(dbPool)

  val modelHistoryStore: ModelHistoryStore = ???

  val modelSnapshotStore: ModelSnapshotStore = ???

  val userStore: DomainUserStore = ???

  def dispose(): Unit = {
    dbPool.close()
  }

}

object OrientDBDomainPersistenceProvider extends App {
  val myList: List[JField] = List(("uri", JString("someVaue")), ("username", JString("root")), ("password", JString("root")))
  val config = JObject(myList)
  
  
  
  val json = parse("""
         { "uri": "test",
           "username": "root",
           "password": "root"
         }
       """)
       
}