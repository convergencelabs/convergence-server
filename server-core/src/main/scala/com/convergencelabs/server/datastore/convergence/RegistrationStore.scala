package com.convergencelabs.server.datastore.convergence

import java.util.UUID

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.orientechnologies.orient.core.record.OElement
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

class RegistrationStore private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
  extends AbstractDatabasePersistence(dbProvider)
  with Logging {

  val Email = "email"
  val FirstName = "fname"
  val LastName = "lname"
  val Token = "token"
  val Approved = "approved"
  val Reason = "reason"

  def addRegistration(fname: String, lname: String, email: String, reason: String): Try[String] = tryWithDb { db =>
    val token = UUID.randomUUID().toString()
    val regDoc: OElement = db.newInstance(Schema.Registration.Class)
    regDoc.setProperty(Email, email);
    regDoc.setProperty(FirstName, fname);
    regDoc.setProperty(LastName, lname);
    regDoc.setProperty(Token, token);
    regDoc.setProperty(Approved, false);
    regDoc.setProperty(Reason, reason)

    db.save(regDoc)
    token
  } recoverWith (handleDuplicateValue)

  def removeRegistration(token: String): Try[Unit] = withDb { db =>
    val command = "DELETE FROM Registration WHERE token = :token"
    val params = Map(Token -> token)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def approveRegistration(token: String): Try[Unit] = withDb { db =>
    val command = "Update Registration Set approved = true WHERE token = :token"
    val params = Map(Token -> token)
    OrientDBUtil.mutateOneDocument(db, command, params)
  }

  def getRegistrationInfo(token: String): Try[Option[Tuple3[String, String, String]]] = withDb { db =>
    val query = "SELECT FROM Registration WHERE token = :token"
    val params = Map(Token -> token)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map { result =>
        val firstName: String = result.field(FirstName)
        val lastName: String = result.field(LastName)
        val email: String = result.field(Email)
        (firstName, lastName, email)
      })
  }

  def isRegistrationApproved(email: String, token: String): Try[Option[Boolean]] = withDb { db =>
    val query = "SELECT FROM Registration WHERE email = :email AND token = :token"
    val params = Map(Email -> email, Token -> token)
    OrientDBUtil
      .findDocument(db, query, params)
      .map(_.map(result => result.getProperty(Approved).asInstanceOf[Boolean]))
  }

  private[this] def handleDuplicateValue[T](): PartialFunction[Throwable, Try[T]] = {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.Registration.Indices.Email =>
          Failure(DuplicateValueException(Email))
        case _ =>
          Failure(e)
      }
  }
}
