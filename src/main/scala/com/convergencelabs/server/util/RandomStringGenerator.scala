/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import java.security.SecureRandom
import java.util.Locale
import java.util.Random

object RandomStringGenerator {
  val UpperCaseLetters: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  val LowerCaseLetters: String = UpperCaseLetters.toLowerCase(Locale.ROOT)
  val Digits: String = "0123456789"
  val AlphaNumeric: String = UpperCaseLetters + LowerCaseLetters + Digits
  val Base64: String = AlphaNumeric + "/" + "+"
}

/**
 * Generates random strings. NOTE this class is NOT thread safe.
 *
 * @param length
 *   The length of the random strings to generate.
 * @param random
 *   The random number generator to use.
 * @param symbols
 *   The symbols to draw upon to generate the random string.
 */
class RandomStringGenerator(
    val length: Int,
    private[this] val random: Random,
    val symbols: String) {

  if (length < 1) {
    throw new IllegalArgumentException("length must be >= 1")
  }
  
  if (symbols.length < 2) {
    throw new IllegalArgumentException("The length of symbols must be >= 2")
  }

  private[this] val buf = new Array[Char](length)

  def this(length: Int, symbols: String) {
    this(length, new SecureRandom(), symbols)
  }
  
  def this(length: Int, random: Random) {
    this(length, random, RandomStringGenerator.Base64)
  }
  
  def this(length: Int) {
    this(length, new SecureRandom())
  }
 
  def nextString(): String = {
    for (idx <- buf.indices) {
      buf(idx) = symbols(random.nextInt(symbols.length))
    }
    new String(buf)
  }
}