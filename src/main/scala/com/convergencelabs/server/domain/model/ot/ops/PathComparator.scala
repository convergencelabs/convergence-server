package com.convergencelabs.server.domain.model.ot

object PathComparator {

  def areEqual(p1: List[Any], p2: List[Any]): Boolean = {
    p1.equals(p2)
  }
  
  def isChildOf(child: List[Any], parent: List[Any]): Boolean = {
    if (child.length != parent.length + 1) {
      return false
    } else {

      for (i <- parent.indices) {
        if (parent(i) != child(i)) {
          return false
        }
      }

      return true
    }
  }

  def isParentOf(parent: List[Any], child: List[Any]): Boolean = {
    return isChildOf(parent, child)
  }

  def isDescendantOf(descendant: List[Any], ancestor: List[Any]): Boolean = {
    if (descendant.length <= ancestor.length) {
      return false
    } else {
      // descendant is longer, so the ancestor length is the potentially common length.
      for (i <- ancestor.indices) {
        if (ancestor(i) != descendant(i)) {
          return false
        }
      }

      return true
    }
  }

  def isAncestorOf(ancestor: List[Any], descendant: List[Any]): Boolean = {
    return isDescendantOf(ancestor, descendant)
  }


  def areSiblings(path1: List[Any], path2: List[Any]): Boolean = {
    if (path1.length != path2.length) {
      return false
    }

    if (path1.isEmpty) {
      return false
    }

    for (i <- 0 until path1.length - 1) {
      if (path1(i) != path2(i)) {
        return false
      }
    }

    if (path1.last == path2.last) {
      return false
    }

    return true
  }
}
