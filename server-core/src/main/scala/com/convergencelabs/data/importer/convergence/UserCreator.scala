package com.convergencelabs.data.importer.convergence

class UserCreator {
  
}

case class CreateConvergenceUser(
    username: String, 
    email: String, 
    firstName: String, 
    lastName: String, 
    displayName: String,
    password: String)
    