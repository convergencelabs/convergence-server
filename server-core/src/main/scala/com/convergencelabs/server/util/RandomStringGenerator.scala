package com.convergencelabs.server.util

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

object RandomStringGenerator {
  val UpperCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  val LowerCaseLetters = UpperCaseLetters.toLowerCase(Locale.ROOT);
  val Digits = "0123456789";
  val AlphaNumeric = UpperCaseLetters + LowerCaseLetters + Digits;
}

class RandomStringGenerator(
    val length: Int,
    private[this] val random: Random,
    val symbols: String) {

  import RandomStringGenerator._

  if (length < 1) {
    throw new IllegalArgumentException();
  }
  
  if (symbols.length() < 2) {
    throw new IllegalArgumentException();
  }

  private[this] val buf = new Array[Char](length);
  private[this] val chars = symbols.toCharArray();

  def this(length: Int, random: Random) {
    this(length, random, RandomStringGenerator.AlphaNumeric);
  }

  def this(length: Int) {
    this(length, new SecureRandom());
  }

 
  def nextString(): String = {
    for (idx <- 0 to buf.length - 1) {
      buf(idx) = symbols(random.nextInt(symbols.length));
    }
    new String(buf);
  }
}