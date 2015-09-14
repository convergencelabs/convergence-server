package com.convergencelabs.server.datastore

import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JObject

trait ConfigurationStore {

    def getConfiguration( configKey: String): JObject

    def setConfiguration(configKey: String,  configuration: JObject);
}
