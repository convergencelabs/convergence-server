package com.convergencelabs.server.datastore.domain

import com.lambdaworks.crypto.SCryptUtil

object PasswordUtil {
  def hashPassword(password: String): String = {
    SCryptUtil.scrypt(password, 16384, 8, 1)
  }
  
  def checkPassword(password: String, hash: String): Boolean = {
    SCryptUtil.check(password, hash)
  }
}