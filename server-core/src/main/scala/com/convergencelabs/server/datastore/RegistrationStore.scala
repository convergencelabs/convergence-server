package com.convergencelabs.server.datastore

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.{ List => JavaList }
import java.util.UUID

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.domain.PasswordUtil
import com.convergencelabs.server.datastore.mapper.UserMapper.ODocumentToUser
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

import grizzled.slf4j.Logging
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

class RegistrationStore private[datastore] (
  private[this] val dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  val Email = "email"
  val FirstName = "fname"
  val LastName = "lname"
  val Token = "token"
  val Approved = "approved"

  def addRegistration(fname: String, lname: String, email: String): Try[CreateResult[String]] = tryWithDb { db =>
    val token = UUID.randomUUID().toString()
    val regDoc = new ODocument("Registration");
    regDoc.field(Email, email);
    regDoc.field(FirstName, fname);
    regDoc.field(LastName, lname);
    regDoc.field(Token, token);
    regDoc.field(Approved, false);

    db.save(regDoc)
    CreateSuccess(token)
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  def removeRegistration(email: String, token: String): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM Registration WHERE email = :email AND token = :token")
    val params = Map(Email -> email, Token -> token)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  def approveRegistration(email: String, token: String): Try[UpdateResult] = tryWithDb { db =>
    val command = new OCommandSQL("Update Registration Set approved = true WHERE email = :email AND token = :token")
    val params = Map(Email -> email, Token -> token)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => UpdateSuccess
    }
  }

  def isRegistrationApproved(email: String, token: String): Try[Option[Boolean]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM Registration WHERE email = :email AND token = :token")
    val params = Map(Email -> email, Token -> token)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    println(results)
    QueryUtil.mapSingletonList(results) {
      result =>
        {
          println(result.field(Approved))
          result.field(Approved, OType.BOOLEAN)
        }
    }
  }
}
