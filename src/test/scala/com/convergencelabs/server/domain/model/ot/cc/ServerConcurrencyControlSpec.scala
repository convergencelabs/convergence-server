package com.convergencelabs.server.domain.model.ot.cc

import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.domain.model.ot.xform.OperationTransformer
import org.mockito.Mockito.{spy, inOrder, verify, times}
import org.scalatest.WordSpec
import org.scalatest.mock.MockitoSugar

class ServerConcurrencyControlSpec extends WordSpec with MockitoSugar {

  "A ServerConcurrencyControl" when {
    "constructed" must {
      "not allow a null transformer" in {
        intercept[NullPointerException] {
          new ServerConcurrencyControl(null, 0)
        }
      }

      "not allow a negative context version transformer" in {
        val xFormer = mock[OperationTransformer]
        intercept[IllegalArgumentException] {
          new ServerConcurrencyControl(xFormer, -1)
        }
      }

      "properly set the contextVersion" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 3)

        assert(scc.contextVersion == 3)
      }

      "have no operation pending" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 3)

        assert(!scc.hasPendingEvent)
      }
    }

    ///////////////////////////////////////////////////////////////////////////

    "adding a client" must {
      "not allow a duplicate clientIds" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        intercept[IllegalArgumentException] {
          scc.trackClient("client1", 0)
        }
      }

      "indicate it has a client after it has been added" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        assert(scc.isClientTracked("client1"))
      }

      "throw an exception if a client is added with a contextVersion greater than the servers" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 10)
        intercept[IllegalArgumentException] {
          scc.trackClient("client1", 11)
        }
      }
    }

    "removing a client" must {
      "not allow an unknown clientId to be removed" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        intercept[IllegalArgumentException] {
          scc.untrackClient("client2")
        }
      }

      "indicate it does not have a client after it has been removed" in {
        val xFormer = mock[OperationTransformer]
        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.untrackClient("client1")
        assert(!scc.isClientTracked("client1"))
      }
    }

    ///////////////////////////////////////////////////////////////////////////

    "processing a remote operation" must {

      "have a pending operation after processing" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.trackClient("client2", 0)

        val op1 = new StringInsertOperation(List(), false, 1, "val")
        val event1 = UnprocessedOperationEvent("client1", 0, op1)

        scc.processRemoteOperation(event1)
        assert(scc.hasPendingEvent)
      }

      "transform concurrent operations from different clients" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.trackClient("client2", 0)

        val op1 = new StringInsertOperation(List(), false, 1, "val")
        val event1 = UnprocessedOperationEvent("client1", 0, op1)

        val op2 = new StringInsertOperation(List(), false, 1, "val")
        val event2 = UnprocessedOperationEvent("client2", 0, op2)

        scc.processRemoteOperation(event1)
        scc.commit()

        scc.processRemoteOperation(event2)
        scc.commit()

        val order = inOrder(xFormer)
        order.verify(xFormer).transform(op2, op1)
      }

      "transforms correct operations as clients context moves forward" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.trackClient("client2", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 1, "c1o1")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 0, client1Op1))
        scc.commit()

        val client1Op2 = new StringInsertOperation(List(), false, 2, "c1o2")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 1, client1Op2))
        scc.commit()

        val client1Op3 = new StringInsertOperation(List(), false, 3, "c1o3")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 2, client1Op3))
        scc.commit()

        val client2Op1 = new StringInsertOperation(List(), false, 1, "c2o1")
        scc.processRemoteOperation(UnprocessedOperationEvent("client2", 0, client2Op1))
        scc.commit()

        val client1Op4 = new StringInsertOperation(List(), false, 4, "c1o4")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 4, client1Op4))
        scc.commit()

        val client1Op5 = new StringInsertOperation(List(), false, 5, "c1o5")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 5, client1Op5))
        scc.commit()

        val client2Op2 = new StringInsertOperation(List(), false, 2, "c202")
        scc.processRemoteOperation(UnprocessedOperationEvent("client2", 2, client2Op2))
        scc.commit()

        val order = inOrder(xFormer)

        // C2 Op1
        order.verify(xFormer).transform(client2Op1, client1Op1)
        order.verify(xFormer).transform(client2Op1, client1Op2)
        order.verify(xFormer).transform(client2Op1, client1Op3)

        // C2 Op2
        order.verify(xFormer).transform(client2Op2, client1Op3)
        order.verify(xFormer).transform(client2Op2, client1Op4)
        order.verify(xFormer).transform(client2Op2, client1Op5)

        verify(xFormer, times(0)).transform(client2Op2, client1Op1)
        verify(xFormer, times(0)).transform(client2Op2, client1Op2)

        verify(xFormer, times(0)).transform(client2Op1, client2Op2)
        verify(xFormer, times(0)).transform(client2Op2, client2Op1)
      }

      "not transform operations from the same site against each other" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.trackClient("client2", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)
        scc.commit()

        val client2Op1 = new StringInsertOperation(List(), false, 0, "B")
        val client2Event1 = UnprocessedOperationEvent("client2", 0, client2Op1)
        scc.processRemoteOperation(client2Event1)
        scc.commit()

        val client1Op2 = new StringInsertOperation(List(), false, 0, "C")
        val client1Event2 = UnprocessedOperationEvent("client1", 0, client1Op2)
        scc.processRemoteOperation(client1Event2)
        scc.commit()

        val order = inOrder(xFormer)
        order.verify(xFormer).transform(client2Op1, client1Op1)
        order.verify(xFormer).transform(client1Op2, client2Op1)

        verify(xFormer, times(0)).transform(client1Op2, client1Op1)
      }

      "throw an exception if the previous event has not been committed or rolled back" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)
        scc.trackClient("client2", 0)

        val op1 = new StringInsertOperation(List(), false, 1, "val")
        val event1 = UnprocessedOperationEvent("client1", 0, op1)
        scc.processRemoteOperation(event1)

        val op2 = new StringInsertOperation(List(), false, 1, "val")
        val event2 = UnprocessedOperationEvent("client2", 0, op2)

        intercept[IllegalStateException] {
          scc.processRemoteOperation(event2)
        }
      }

      "throw an exception if an event is received from an unknown client" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client3", 0, client1Op1)
        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(client1Event1)
        }
      }

      "throw an exception if a client's contextVersion decreases" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 10)
        scc.trackClient("client1", 0)

        val c1o1 = new StringInsertOperation(List(), false, 1, "c1o1")
        scc.processRemoteOperation(UnprocessedOperationEvent("client1", 10, c1o1))
        scc.commit()

        val c1o2 = new StringInsertOperation(List(), false, 12, "c1o1")

        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(UnprocessedOperationEvent("client1", 9, c1o2))
        }
      }

      "throw an exception if a client's contextVersion is greater than the servers" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 10)
        scc.trackClient("client1", 10)

        val c1o2 = new StringInsertOperation(List(), false, 1, "c1o1")
        intercept[IllegalArgumentException] {
          scc.processRemoteOperation(UnprocessedOperationEvent("client1", 11, c1o2))
        }
      }
    }

    "committing an operation" must {

      "increment the contextVersion after a commit" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        assert(scc.contextVersion == 0)
        scc.commit()
        assert(scc.contextVersion == 1)
      }

      "have no operation pending after commit" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        scc.commit()
        assert(!scc.hasPendingEvent)
      }

      "throw an exception if no operation is pending" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        scc.commit()
        intercept[IllegalStateException] {
          scc.commit()
        }
      }
    }

    "rolling back an operation" must {
      "not increment the contextVersion after rollback" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        assert(scc.contextVersion == 0)
        scc.rollback()
        assert(scc.contextVersion == 0)
      }

      "have no operation pending after rollback" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        scc.rollback()
        assert(!scc.hasPendingEvent)
      }

      "throw an exception if no operation is pending" in {
        val xFormer = spy(new MockOperationTransformer())

        val scc = new ServerConcurrencyControl(xFormer, 0)
        scc.trackClient("client1", 0)

        val client1Op1 = new StringInsertOperation(List(), false, 0, "A")
        val client1Event1 = UnprocessedOperationEvent("client1", 0, client1Op1)
        scc.processRemoteOperation(client1Event1)

        scc.rollback()
        intercept[IllegalStateException] {
          scc.rollback()
        }
      }
    }
  }

  class MockOperationTransformer extends OperationTransformer {
    override def transform(op1: Operation, op2: Operation): (Operation, Operation) = {
      (op1, op2)
    }
  }

}

