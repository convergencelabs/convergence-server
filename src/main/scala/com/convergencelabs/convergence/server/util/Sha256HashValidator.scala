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

package com.convergencelabs.convergence.server.util

/**
 * A utility object to validate a SHA256 has of a block of text.
 */
object Sha256HashValidator {

  /**
   * Indicates that the hash did not match the expected value.
   *
   * @param expected The expected hash value.
   * @param actual   The actual hash value computed from the input text.
   */
  final case class HashValidationFailure(expected: String, actual: String)

  /**
   * Validates that the SHA256 hash of the supplied text matches the
   * supplied hash.
   *
   * @param text         The text to compute the hash for and validate.
   * @param expectedHash The expected hash value.
   * @return Right(()) upon success, or Left(error) if validation fails.
   */
  def validateHash(text: String, expectedHash: String): Either[HashValidationFailure, Unit] = {
    val hash = sha256(text)
    if (hash != expectedHash) {
      Left(HashValidationFailure(expectedHash, hash))
    } else {
      Right(())
    }
  }

  private[this] def sha256(s: String): String = {
    val m = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    m.map("%02x".format(_)).mkString
  }
}
