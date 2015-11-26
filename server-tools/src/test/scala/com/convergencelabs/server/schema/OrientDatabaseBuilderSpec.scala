package com.convergencelabs.server.schema

import org.scalatest.WordSpec
import org.scalatest.Matchers

class DatabaseBuilderSpec
    extends WordSpec
    with Matchers {

  "OrientDatabaseBuilder" when {

    "building a database" must {
      "work" in {
        val foo = new OrientDatabaseBuilder(
          Some("schema/convergence-database.json"),
          None,
          None,
          true)
        foo.buildSchema()
      }
    }
  }
}