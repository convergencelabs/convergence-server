package com.convergencelabs.server.datastore.convergence

object Schema {
  object User {
    val Class = "User"

    object Indices {
      val Username = "User.username"
      val Email = "User.email"
    }
  }
  
  object UserCredential {
    val Class = "UserCredential"
  }

  object Domain {
    val Class = "Domain"

    object Indices {
      val NamespaceId = "Domain.namespace_id"
    }
  }

  object DomainDatabase {
    val Class = "DomainDatabase"

    object Indices {
      val Domain = "DomainDatabase.domain"
      val Database = "DomainDatabase.database"
    }
  }

  object ConvergenceDelta {
    val Class = "ConvergenceDelta"

    object Indices {
      val DeltaNo = "ConvergenceDelta.deltaNo"
    }
  }

  object ConvergenceDeltaHistory {
    val Class = "ConvergenceDeltaHistory"

    object Indices {
      val Delta = "ConvergenceDeltaHistory.delta"
    }
  }

  object DomainDelta {
    val Class = "DomainDelta"

    object Indices {
      val DeltaNo = "DomainDelta.deltaNo"
    }
  }

  object DomainDeltaHistory {
    val Class = "DomainDeltaHistory"

    object Indices {
      val DomainDelta = "DomainDeltaHistory.domain_delta"
    }
  }

  object Permission {
    val Class = "Permission"

    object Indices {
      val Id = "Permission.id"
    }
  }

  object Role {
    val Class = "Role"

    object Indices {
      val Id = "Role.id"
    }
  }

  object UserDomainRole {
    val Class = "UserDomainRole"
  }

  object Registration {
    val Class = "Registration"

    object Indices {
      val Email = "Registration.email"
    }
  }
}