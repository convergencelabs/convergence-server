package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }
import java.util.UUID

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Try

import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging

class RegistrationStore private[datastore] (
  private[this] val dbProvider: DatabaseProvider)
    extends AbstractDatabasePersistence(dbProvider)
    with Logging {
  
  val EmailIndex = "Registration.email"

  val Email = "email"
  val FirstName = "fname"
  val LastName = "lname"
  val Token = "token"
  val Approved = "approved"
  val Reason = "reason"

  def addRegistration(fname: String, lname: String, email: String, reason: String): Try[String] = tryWithDb { db =>
    val token = UUID.randomUUID().toString()
    val regDoc = new ODocument("Registration");
    regDoc.field(Email, email);
    regDoc.field(FirstName, fname);
    regDoc.field(LastName, lname);
    regDoc.field(Token, token);
    regDoc.field(Approved, false);
    regDoc.field(Reason, reason)

    db.save(regDoc)
    token
  } recoverWith {
    case e: ORecordDuplicatedException => handleDuplicateValue[String](e)
  }

  def removeRegistration(token: String): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Registration WHERE token = :token")
    val params = Map(Token -> token)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => throw new EntityNotFoundException()
      case _ => ()
    }
  }

  def approveRegistration(token: String): Try[Unit] = tryWithDb { db =>
    val command = new OCommandSQL("Update Registration Set approved = true WHERE token = :token")
    val params = Map(Token -> token)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => throw new EntityNotFoundException()
      case _ => ()
    }
  }

  def getRegistrationInfo(token: String): Try[Option[Tuple3[String, String, String]]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Registration WHERE token = :token")
    val params = Map(Token -> token)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { result =>
      {
        val firstName: String = result.field(FirstName)
        val lastName: String = result.field(LastName)
        val email: String = result.field(Email)
        (firstName, lastName, email)
      }
    }
  }

  def isRegistrationApproved(email: String, token: String): Try[Option[Boolean]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Registration WHERE email = :email AND token = :token")
    val params = Map(Email -> email, Token -> token)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { result =>
      {
        val approved: Boolean = result.field(Approved, OType.BOOLEAN)
        approved
      }
    }
  }

  private[this] def handleDuplicateValue[T](e: ORecordDuplicatedException): Try[T] = {
    e.getIndexName match {
      case EmailIndex =>
        Failure(DuplicateValueExcpetion(Email))
      case _ =>
        Failure(e)
    }
  }
}
