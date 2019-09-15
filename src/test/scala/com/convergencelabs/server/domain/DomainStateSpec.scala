package com.convergencelabs.server.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpec

class DomainStatusSpec
    extends WordSpec
    with Matchers {

  "A DomainStatus" when {
    "Getting a value from a string" must {
      "return Initializing when the string is 'initializing'" in {
        DomainStatus.withName("initializing") shouldBe DomainStatus.Initializing
      }
      
      "return Online when the string is 'online'" in {
        DomainStatus.withName("online") shouldBe DomainStatus.Online
      }
      
      "return Offline when the string is 'offline'" in {
        DomainStatus.withName("offline") shouldBe DomainStatus.Offline
      }
      
      "return Error when the string is 'error'" in {
        DomainStatus.withName("error") shouldBe DomainStatus.Error
      }
      
      "return Maintenance when the string is 'maintenance'" in {
        DomainStatus.withName("maintenance") shouldBe DomainStatus.Maintenance
      }
      
      "return Terminiating when the string is 'terminating'" in {
        DomainStatus.withName("deleting") shouldBe DomainStatus.Deleting
      }
    }
    
    "calling toString" must {
      "return terminiating when the string is 'Terminating'" in {
        DomainStatus.Deleting.toString() shouldBe "deleting"
      }
    }
  }
}
