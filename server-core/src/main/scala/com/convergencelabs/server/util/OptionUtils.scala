package com.convergencelabs.server.util

object OptionUtils {
  def toNullable[T >: Null](option: Option[T]): T = {
    option match {
      case Some(value) => value
      case None => null
    }
  }
}