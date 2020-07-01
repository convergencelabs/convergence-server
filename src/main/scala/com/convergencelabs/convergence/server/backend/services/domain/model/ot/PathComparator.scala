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

/**
 * PathComparator is a utility class to help compare operation paths. Operation
 * paths are represented as Lists of Strings an Integers.  Strings represent a
 * position within an array, and strings represent properties within objects.
 */
private[ot] object PathComparator {

  /**
   * Determines if the two paths are equal.  Paths are equal if they are of the
   * same length and contain equal elements at all positions.
   *
   * @param p1 The first path to compare.
   * @param p2 The second path to compare.
   *
   * @return True if the two path lists represent the same path, false otherwise.
   */
  def areEqual(p1: List[Any], p2: List[Any]): Boolean = {
    p1.equals(p2)
  }

  /**
   * Determines if the first path passed in is the direct child of the second
   * path passed in.  A path is a direct child of another if the child path
   * is exactly one element longer than the parent AND if for each element in
   * the parent path there is an equal element in the child path at the same
   * position.
   *
   * @param p1 The potential child path.
   * @param p2 The potential parent path.
   *
   * @return True if the p1 is a direct child of p2
   */
  def isChildOf(p1: List[Any], p2: List[Any]): Boolean = {
    if (p1.length != p2.length + 1) {
      false
    } else {
      // parent is shorter by one.
      p2.indices.forall(i => p2(i) == p1(i))
    }
  }

  /**
   * Determines if the first path passed in is the direct parent of the second
   * path passed in.  A path is a direct parent of another if the parent path
   * is exactly one element shorter than the parent AND if for each element in
   * the parent path there is an equal element in the child path at the same
   * position.
   *
   * @param p1 The potential parent path.
   * @param p2 The potential child path.
   *
   * @return True if the p1 is a direct parent of p2
   */
  def isParentOf(p1: List[Any], p2: List[Any]): Boolean = {
    isChildOf(p2, p1)
  }

  /**
   * Determines if the first path passed in is a descendant of the second
   * path passed in.  A path is a descendant of another if the path is
   * longer than the other path AND if for each element in the other path
   * the descendant path has an equal element at the same position.
   *
   * @param p1 The potential descendant path.
   * @param p2 The potential ancestor path.
   *
   * @return True if the p1 is a descendant of p2
   */
  def isDescendantOf(p1: List[Any], p2: List[Any]): Boolean = {
    if (p1.length <= p2.length) {
      false
    } else {
      // descendant is longer, so the ancestor length is the potentially common length.
      p2.indices.forall(i => p2(i) == p1(i))
    }
  }

  /**
   * Determines if the first path passed in is an ancestor of the second
   * path passed in.  A path is a ancestor of another if the path is
   * shorter than the other path AND if for each element in ancestor path the
   * descendant path has an equal element at the same position.
   *
   * @param p1 The potential ancestor path.
   * @param p2 The potential descendant path.
   *
   * @return True if the p1 is a ancestor of p2
   */
  def isAncestorOf(p1: List[Any], p2: List[Any]): Boolean = {
    isDescendantOf(p2, p1)
  }

  /**
   * Determines if two paths are siblings of each other.  Two paths
   * are siblings if they are 1) the same length, 2) contain equal
   * elements at all positions except for the last position and 3)
   * neither are the root path.
   *
   * @param p1 The first path to compare
   * @param p2 The second path to compare
   *
   * @return True if the paths are siblings, false otherwise.
   */
  def areSiblings(p1: List[Any], p2: List[Any]): Boolean = {
    if (p1.length != p2.length) {
      false
    } else if (p1.isEmpty) {
      false
    } else if (p1.last == p2.last) {
      false
    } else {
      p1.indices.dropRight(1).forall(i => p1(i) == p2(i))
    }
  }
}
