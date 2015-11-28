package com.convergencelabs.server.datastore.domain

object PasswordGenUtil extends App {
  val password = "password"
  val hash = PasswordUtil.hashPassword(password)
  println(hash)
  
  println(PasswordUtil.checkPassword(password, hash))
}