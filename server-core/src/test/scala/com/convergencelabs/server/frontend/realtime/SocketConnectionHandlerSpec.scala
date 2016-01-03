package com.convergencelabs.server.frontend.realtime

import scala.language.postfixOps

import org.mockito.Mockito
import org.mockito.Mockito.times
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.mock.MockitoSugar

import com.convergencelabs.server.domain.DomainFqn

class SocketConnectionHandlerSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar {

  "A SocketConnectionHandler" when {
    "notifying listeners" must {
      "notify each added listener only once" in new TestFixture {
        handler.addListener(listener1)
        handler.addListener(listener2)
        handler.fireOnSocketOpen(domainFqn, socket)

        Mockito.verify(listener1, times(1)).apply(domainFqn, socket)
        Mockito.verify(listener2, times(1)).apply(domainFqn, socket)
      }
    }

    "adding a listener" must {
      "ignore duplicate additions" in new TestFixture {
        handler.addListener(listener1)
        handler.addListener(listener1)
        handler.fireOnSocketOpen(domainFqn, socket)
        Mockito.verify(listener1, times(1)).apply(domainFqn, socket)
      }
    }

    "removing a listener" must {
      "no longer notify removed listeners" in new TestFixture {
        handler.addListener(listener1)
        handler.addListener(listener2)
        handler.removeListener(listener1)
        handler.fireOnSocketOpen(domainFqn, socket)
        Mockito.verify(listener1, times(0)).apply(domainFqn, socket)
        Mockito.verify(listener2, times(1)).apply(domainFqn, socket)
      }
    }
  }

  trait TestFixture {
    val socket = mock[ConvergenceServerSocket]
    val domainFqn = mock[DomainFqn]

    val listener1 = mock[Function2[DomainFqn, ConvergenceServerSocket, Unit]]
    val listener2 = mock[Function2[DomainFqn, ConvergenceServerSocket, Unit]]

    val handler = new SocketConnectionHandler()
  }
}
