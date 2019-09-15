package com.convergencelabs.server.domain.model.ot

import org.scalatest.WordSpec
import org.scalatest.Matchers

class PathComparatorSpec
    extends WordSpec
    with Matchers {

  val parent = List("level1")

  val child = parent :+ "level2"
  val grandChild = child :+ "level3"
  val greatGrandChild = grandChild :+ "level4"

  val childSibling = parent :+ "level2a"
  val grandChildSibling = childSibling :+ "level3a"
  val deeperCousin = grandChildSibling :+ "level4a"

  "A PathComparator" when {

    "evalauting a decendant path relationship" must {
      "return true for a grand child being a descendant of its grand parent" in {
        val foo = PathComparator.isDescendantOf(grandChild, parent)
        foo shouldBe true
      }

      "return true for a child being a descendant of its direct parent" in {
        PathComparator.isDescendantOf(child, parent) shouldBe true
      }

      "return false for a sibling paths" in {
        PathComparator.isDescendantOf(childSibling, child) shouldBe false
      }

      "return false for a child testing a parent path" in {
        PathComparator.isDescendantOf(parent, child) shouldBe false
      }

      "return false for a grandshild testing a deeper cousin" in {
        PathComparator.isDescendantOf(deeperCousin, grandChild) shouldBe false
      }
    }

    "evalauting a child path relationship" must {

      "return true for a child under a parent" in {
        PathComparator.isChildOf(child, parent) shouldBe true
      }

      "return false for a parent being a child of its child" in {
        PathComparator.isChildOf(parent, child) shouldBe false
      }

      "return false for a indirect descendant being a child of its ancestor" in {
        PathComparator.isChildOf(grandChild, parent) shouldBe false
      }

      "return false for a sibling being a child of a sibling" in {
        PathComparator.isChildOf(grandChildSibling, child) shouldBe false
      }
    }

    "evalauting an ancestor path relationship" must {
      "return true for a parent being an ancestor of a direct child" in {
        PathComparator.isAncestorOf(parent, child) shouldBe true
      }

      "return true for a parent being an ancestor of an indirec direct descendant" in {
        PathComparator.isAncestorOf(parent, grandChild) shouldBe true
      }

      "return false for a child being an ancestor of its parent" in {
        PathComparator.isAncestorOf(child, parent) shouldBe false
      }

      "return false for a higher order sibling being an ancestor of a siblings descendant" in {
        PathComparator.isAncestorOf(child, grandChildSibling) shouldBe false
      }
    }

    "evalauting a parent path relationship" must {
      "return true for a parent being an ancestor of a direct child" in {
        PathComparator.isParentOf(parent, child) shouldBe true
      }

      "return false for a parent being an ancestor of an indirect descendant" in {
        PathComparator.isParentOf(parent, grandChild) shouldBe false
      }

      "return false for a child being an ancestor of its parent" in {
        PathComparator.isParentOf(child, parent) shouldBe false
      }

      "return false for an path being an ancestor of a siblings child" in {
        PathComparator.isParentOf(child, grandChildSibling) shouldBe false
      }
    }

    "evalauting a sibling path relationship" must {
      "return true for an path being a sibling of a sibling" in {
        PathComparator.areSiblings(child, childSibling) shouldBe true
      }

      "return false for a path being a sibling of itself" in {
        PathComparator.areSiblings(child, child) shouldBe false
      }

      "return false for a parent being a sibling of a child" in {
        PathComparator.areSiblings(parent, child) shouldBe false
      }

      "return false for a child being a sibling of its parent" in {
        PathComparator.areSiblings(child, parent) shouldBe false
      }

      "return false for a child being a sibling of its cousin" in {
        PathComparator.areSiblings(grandChildSibling, grandChild) shouldBe false
      }

      "return false for two empty paths being siblings" in {
        PathComparator.areSiblings(List(), List()) shouldBe false
      }
    }

    "evalauting the equality of paths" must {
      "return true paths that are equal" in {
        // scalastyle:off multiple.string.literals
        PathComparator.areEqual(List("1", 2, "3"), List("1", 2, "3")) shouldBe true
        // scalastyle:on multiple.string.literals
      }

      "return false for paths that are unequal" in {
        // scalastyle:off multiple.string.literals
        PathComparator.areEqual(List("1", 2, "3"), List(1, "2", 3)) shouldBe false
        // scalastyle:on multiple.string.literals
      }
    }
  }
}
