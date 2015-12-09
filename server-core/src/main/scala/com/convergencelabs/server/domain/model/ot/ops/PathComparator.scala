package com.convergencelabs.server.domain.model.ot

private[ot] object PathComparator {

  def areEqual(p1: List[Any], p2: List[Any]): Boolean = {
    p1.equals(p2)
  }

  def isChildOf(child: List[Any], parent: List[Any]): Boolean = {
    if (child.length != parent.length + 1) {
      false
    } else {
      // parent is shorter by one.
      parent.indices.forall(i => parent(i) == child(i))
    }
  }

  def isParentOf(parent: List[Any], child: List[Any]): Boolean = {
    isChildOf(child, parent)
  }

  def isDescendantOf(descendant: List[Any], ancestor: List[Any]): Boolean = {
    if (descendant.length <= ancestor.length) {
      false
    } else {
      // descendant is longer, so the ancestor length is the potentially common length.
      ancestor.indices.forall(i => ancestor(i) == descendant(i))
    }
  }

  def isAncestorOf(ancestor: List[Any], descendant: List[Any]): Boolean = {
     isDescendantOf(descendant, ancestor)
  }

  def areSiblings(path1: List[Any], path2: List[Any]): Boolean = {
    if (path1.length != path2.length) {
      false
    } else if (path1.isEmpty) {
      false
    } else if (path1.last == path2.last) {
      false
    } else {
      path1.indices.dropRight(1).forall(i => path1(i) == path2(i))
    }
  }
}
