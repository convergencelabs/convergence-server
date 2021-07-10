/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform.{OperationTransformer, ReferenceTransformer, TransformationFunctionRegistry}
import org.mockito.Mockito.{inOrder, spy, times, verify}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

final class ServerConcurrencyControlSpec extends AnyWordSpec with MockitoSugar {

  private val Client1 = "client1"
  private val Client2 = "client2"
  private val Val = "val"
  private val valueId = "valueId"

  "A ServerConcurrencyControl" when {
    "constructed" must {
      "not allow a negative context version" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val error = ServerConcurrencyControl.create(opXFormer, refXFormer, -1).failed.get
        assert(error.isInstanceOf[IllegalArgumentException])
      }

      "properly set the contextVersion" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 3).get

        assert(scc.contextVersion == 3)
      }

      "have no operation pending" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 3).get

        assert(!scc.hasPendingEvent)
      }
    }

    ///////////////////////////////////////////////////////////////////////////

    "adding a client" must {
      "not allow a duplicate clientIds" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        intercept[IllegalArgumentException] {
          scc.trackClient(Client1, 0)
        }
      }

      "indicate it has a client after it has been added" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        assert(scc.isClientTracked(Client1))
      }

      "throw an exception if a client is added with a contextVersion greater than the servers" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 10).get
        intercept[IllegalArgumentException] {
          scc.trackClient(Client1, 11)
        }
      }
    }

    "removing a client" must {
      "not allow an unknown clientId to be removed" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        intercept[IllegalArgumentException] {
          scc.untrackClient(Client2)
        }
      }

      "indicate it does not have a client after it has been removed" in {
        val opXFormer = mock[OperationTransformer]
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.untrackClient(Client1)
        assert(!scc.isClientTracked(Client1))
      }
    }

    ///////////////////////////////////////////////////////////////////////////

    "processing a remote operation" must {

      "have a pending operation after processing" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.trackClient(Client2, 0)

        val op1 = StringSpliceOperation(valueId, noOp = false, 1, 0, Val)
        val event1 = UnprocessedOperationEvent(Client1, 0, op1)

        scc.processRemoteOperation(event1).get
        assert(scc.hasPendingEvent)
      }

      "transform concurrent operations from different clients" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.trackClient(Client2, 0)

        val op1 = StringSpliceOperation(valueId, noOp = false, 1, 0, Val)
        val event1 = UnprocessedOperationEvent(Client1, 0, op1)

        val op2 = StringSpliceOperation(valueId, noOp = false, 1, 0, Val)
        val event2 = UnprocessedOperationEvent(Client2, 0, op2)

        scc.processRemoteOperation(event1).get
        scc.commit()

        scc.processRemoteOperation(event2).get
        scc.commit()

        val order = inOrder(opXFormer)
        order.verify(opXFormer).transform(op2, op1)
      }

      "transforms correct operations as clients context moves forward" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.trackClient(Client2, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 1, 0, "c1o1")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 0, client1Op1)).get
        scc.commit()

        val client1Op2 = StringSpliceOperation(valueId, noOp = false, 2, 0, "c1o2")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 1, client1Op2)).get
        scc.commit()

        val client1Op3 = StringSpliceOperation(valueId, noOp = false, 3, 0, "c1o3")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 2, client1Op3)).get
        scc.commit()

        val client2Op1 = StringSpliceOperation(valueId, noOp = false, 1, 0, "c2o1")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client2, 0, client2Op1)).get
        scc.commit()

        val client1Op4 = StringSpliceOperation(valueId, noOp = false, 4, 0, "c1o4")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 4, client1Op4)).get
        scc.commit()

        val client1Op5 = StringSpliceOperation(valueId, noOp = false, 5, 0, "c1o5")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 5, client1Op5)).get
        scc.commit()

        val client2Op2 = StringSpliceOperation(valueId, noOp = false, 2, 0, "c202")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client2, 2, client2Op2)).get
        scc.commit()

        val order = inOrder(opXFormer)

        // C2 Op1
        order.verify(opXFormer).transform(client1Op1, client2Op1)
        order.verify(opXFormer).transform(client1Op2, client2Op1)
        order.verify(opXFormer).transform(client1Op3, client2Op1)

        // C2 Op2
        order.verify(opXFormer).transform(client1Op3, client2Op2)
        order.verify(opXFormer).transform(client1Op4, client2Op2)
        order.verify(opXFormer).transform(client1Op5, client2Op2)

        verify(opXFormer, times(0)).transform(client1Op1, client2Op2)
        verify(opXFormer, times(0)).transform(client1Op2, client2Op2)

        verify(opXFormer, times(0)).transform(client2Op2, client2Op1)
        verify(opXFormer, times(0)).transform(client2Op1, client2Op2)
      }

      "not transform operations from the same site against each other" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.trackClient(Client2, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get
        scc.commit()

        val client2Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "B")
        val client2Event1 = UnprocessedOperationEvent(Client2, 0, client2Op1)
        scc.processRemoteOperation(client2Event1).get
        scc.commit()

        val client1Op2 = StringSpliceOperation(valueId, noOp = false, 0, 0, "C")
        val client1Event2 = UnprocessedOperationEvent(Client1, 0, client1Op2)
        scc.processRemoteOperation(client1Event2).get
        scc.commit()

        val order = inOrder(opXFormer)
        order.verify(opXFormer).transform(client1Op1, client2Op1)
        order.verify(opXFormer).transform(client2Op1, client1Op2)

        verify(opXFormer, times(0)).transform(client1Op1, client1Op2)
      }

      "throw an exception if the previous event has not been committed or rolled back" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)
        scc.trackClient(Client2, 0)

        val op1 = StringSpliceOperation(valueId, noOp = false, 1, 0, Val)
        val event1 = UnprocessedOperationEvent(Client1, 0, op1)
        scc.processRemoteOperation(event1).get

        val op2 = StringSpliceOperation(valueId, noOp = false, 1, 0, Val)
        val event2 = UnprocessedOperationEvent(Client2, 0, op2)

        intercept[IllegalStateException] {
          scc.processRemoteOperation(event2).get
        }
      }

      "throw an exception if an event is received from an unknown client" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client3", 0, client1Op1)
        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(client1Event1).get
        }
      }

      "throw an exception if a client's contextVersion decreases" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 10).get
        scc.trackClient(Client1, 0)

        val c1o1 = StringSpliceOperation(valueId, noOp = false, 1, 0, "c1o1")
        scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 10, c1o1)).get
        scc.commit()

        val c1o2 = StringSpliceOperation(valueId, noOp = false, 12, 0, "c1o1")

        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 9, c1o2)).get
        }
      }

      "throw an exception if a client's contextVersion is greater than the servers" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 10).get
        scc.trackClient(Client1, 10)

        val c1o2 = StringSpliceOperation(valueId, noOp = false, 1, 0, "c1o1")
        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(UnprocessedOperationEvent(Client1, 11, c1o2)).get
        }
      }
    }

    "committing an operation" must {

      "increment the contextVersion after a commit" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        assert(scc.contextVersion == 0)
        scc.commit()
        assert(scc.contextVersion == 1)
      }

      "have no operation pending after commit" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        scc.commit()
        assert(!scc.hasPendingEvent)
      }

      "throw an exception if no operation is pending" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        scc.commit()
        intercept[IllegalStateException] {
          scc.commit()
        }
      }
    }

    "rolling back an operation" must {
      "not increment the contextVersion after rollback" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]
        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        assert(scc.contextVersion == 0)
        scc.rollback()
        assert(scc.contextVersion == 0)
      }

      "have no operation pending after rollback" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        scc.rollback()
        assert(!scc.hasPendingEvent)
      }

      "throw an exception if no operation is pending" in {
        val opXFormer = spy(new MockOperationTransformer())
        val refXFormer = mock[ReferenceTransformer]

        val scc = ServerConcurrencyControl.create(opXFormer, refXFormer, 0).get
        scc.trackClient(Client1, 0)

        val client1Op1 = StringSpliceOperation(valueId, noOp = false, 0, 0, "A")
        val client1Event1 = UnprocessedOperationEvent(Client1, 0, client1Op1)
        scc.processRemoteOperation(client1Event1).get

        scc.rollback()
        intercept[IllegalStateException] {
          scc.rollback()
        }
      }
    }
  }

  class MockOperationTransformer extends OperationTransformer(new TransformationFunctionRegistry()) {

    override def transform(op1: Operation, op2: Operation): (Operation, Operation) = {
      (op1, op2)
    }
  }

}
