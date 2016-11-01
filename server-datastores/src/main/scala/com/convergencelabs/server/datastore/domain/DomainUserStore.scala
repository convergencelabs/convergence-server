package com.convergencelabs.server.datastore.domain

import java.util.{ List => JavaList }
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Try
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.mapper.DomainUserMapper.DomainUserToODocument
import com.convergencelabs.server.datastore.domain.mapper.DomainUserMapper.ODocumentToDomainUser
import com.convergencelabs.server.domain.DomainUser
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import grizzled.slf4j.Logging
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashTable
import scala.collection.mutable.HashTable
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateDomainUser
import java.util.UUID
import com.orientechnologies.orient.core.metadata.sequence.OSequence.CreateParams
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import java.util.Base64
import java.lang.Long
import java.math.BigInteger
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser
import java.time.Instant
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.index.OCompositeKey
import sun.util.calendar.JulianCalendar.Date
import java.util.Date

object DomainUserStore {

  case class CreateNormalDomainUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String])

  case class UpdateDomainUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String])

  case class CreateDomainUser(
    userType: DomainUserType.Value,
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String])
}

/**
 * Manages the persistence of Domain Users.  This class manages both user profile records
 * as well as user credentials for users authenticated by Convergence itself.
 *
 * @constructor Creates a new DomainUserStore using the provided connection pool to
 * connect to the database
 *
 * @param dbPool The database pool to use.
 */
class DomainUserStore private[domain] (private[this] val dbPool: OPartitionedDatabasePool)
    extends AbstractDatabasePersistence(dbPool)
    with Logging {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  val Username = "username"
  val Password = "password"
  val UserType = "userType"
  val UidSeq = "UIDSEQ"
  val SessionSeq = "SESSIONSEQ"
  val UsernameIndex = "User.username"
  val LastLogin = "lastLogin"

  /**
   * Creates a new domain user in the system, and optionally set a password.
   * Note the uid as well as the username of the user must be unique among all
   * users in this domain.
   *
   * @param domainUser The user to add to the system.
   *
   * @param password An optional password if internal authentication is to
   * be used for this user.
   *
   * @return A String representing the created users uid.
   */
  def createNormalDomainUser(domainUser: CreateNormalDomainUser, password: Option[String]): Try[CreateResult[String]] = {
    val normalUser = CreateDomainUser(
      DomainUserType.Normal,
      domainUser.username,
      domainUser.firstName,
      domainUser.lastName,
      domainUser.displayName,
      domainUser.email)
    this.createDomainUser(normalUser, password)
  }

  def createAnonymousDomainUser(displayName: Option[String]): Try[CreateResult[String]] = {
    // FIXME make a sequence
    val username = UUID.randomUUID().toString()
    val anonymousUser = CreateDomainUser(
      DomainUserType.Anonymous,
      username,
      None,
      None,
      displayName,
      None)

    this.createDomainUser(anonymousUser, None)
  }

  def createAdminDomainUser(convergenceUsername: String): Try[CreateResult[String]] = {
    val adminUser = CreateDomainUser(
      DomainUserType.Admin,
      convergenceUsername,
      None,
      None,
      Some(convergenceUsername + "(Admin)"),
      None)

    this.createDomainUser(adminUser, None)
  }

  private[this] def createDomainUser(domainUser: CreateDomainUser, password: Option[String]): Try[CreateResult[String]] = tryWithDb { db =>
    val create = DomainUser(
      domainUser.userType,
      domainUser.username,
      domainUser.firstName,
      domainUser.lastName,
      domainUser.displayName,
      domainUser.email)

    val userDoc = create.asODocument
    db.save(userDoc)
    userDoc.reload()

    val pwDoc = db.newInstance("UserCredential")
    pwDoc.field("user", userDoc, OType.LINK) // FIXME verify this creates a link and now a new doc.

    password match {
      case Some(pass) => pwDoc.field(Password, PasswordUtil.hashPassword(pass))
      case None => pwDoc.field(Password, null, OType.STRING) // scalastyle:off null
    }

    db.save(pwDoc)

    CreateSuccess((domainUser.username))
  } recover {
    case e: ORecordDuplicatedException => DuplicateValue
  }

  /**
   * Deletes a single domain user by uid.
   *
   * @param the uid of the user to delete.
   */
  def deleteDomainUser(username: String): Try[DeleteResult] = tryWithDb { db =>
    val command = new OCommandSQL("DELETE FROM User WHERE username = :username AND userType = 'normal'")
    val params = Map(Username -> username)
    val count: Int = db.command(command).execute(params.asJava)
    count match {
      case 0 => NotFound
      case _ => DeleteSuccess
    }
  }

  /**
   * Updates a DomainUser with new information.  The uid of the domain user argument must
   * correspond to an existing user in the database.
   *
   * @param domainUser The user to update.
   */
  def updateDomainUser(update: UpdateDomainUser): Try[UpdateResult] = tryWithDb { db =>
    val UpdateDomainUser(username, firstName, lastName, displayName, email) = update;
    val domainUser = DomainUser(DomainUserType.Normal, username, firstName, lastName, displayName, email)
    
    val updatedDoc = domainUser.asODocument

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username AND userType = 'normal'")
    val params = Map(Username -> domainUser.username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        try {
          doc.merge(updatedDoc, false, false)
          db.save(doc)
          UpdateSuccess
        } catch {
          case e: ORecordDuplicatedException => InvalidValue
        }
      case None => NotFound
    }
  }

  /**
   * Gets a single domain user by usernam.
   *
   * @param username The uid of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified username exists, or None if no such user exists.
   */
  def getDomainUserByUsername(username: String): Try[Option[DomainUser]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username AND userType = 'normal'")
    val params = Map(Username -> username)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { _.asDomainUser }
  }

  /**
   * Gets a list of domain users matching any of a list of usernames.
   *
   * @param uids The list of usernames of the users to retrieve.
   *
   * @return A list of users matching the list of supplied usernames.
   */
  def getDomainUsersByUsername(usernames: List[String]): Try[List[DomainUser]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username in :usernames AND userType = 'normal'")
    val params = Map("usernames" -> usernames.asJava)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList.map { _.asDomainUser }
  }

  /**
   * Gets a single domain user by email.
   *
   * @param email The email of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified email exists, or None if no such user exists.
   */
  def getDomainUserByEmail(email: String): Try[Option[DomainUser]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE email = :email AND userType = 'normal'")
    val params = Map("email" -> email)
    val results: JavaList[ODocument] = db.command(query).execute(params.asJava)
    QueryUtil.mapSingletonList(results) { _.asDomainUser }
  }

  /**
   * Gets a list of domain users matching any of a list of emails.
   *
   * @param uids The list of emails of the users to retrieve.
   *
   * @return A list of users matching the list of supplied emails.
   */
  def getDomainUsersByEmail(emails: List[String]): Try[List[DomainUser]] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE email in :emails AND userType = 'normal'")
    val params = Map("emails" -> emails.asJava)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList.map { doc => doc.asDomainUser }
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   *
   * @return true if the user exists, false otherwise.
   */
  def domainUserExists(username: String): Try[Boolean] = {
    this.userExists(username, DomainUserType.Normal)
  }
  
  def adminUserExists(username: String): Try[Boolean] = {
    this.userExists(username, DomainUserType.Admin)
  }
  
  
  private[this] def userExists(username: String, userType: DomainUserType.Value): Try[Boolean] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username AND userType = :userType")
    val params = Map(Username -> username, UserType -> userType.toString.toLowerCase)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)
    result.asScala.toList match {
      case doc :: Nil => true
      case _ => false
    }
  }

  /**
   * Gets a listing of all domain users based on ordering and paging.
   *
   * @param orderBy The property of the domain user to order by. Defaults to username.
   * @param sortOrder The order (ascending or descending) of the ordering. Defaults to descending.
   * @param limit maximum number of users to return.  Defaults to unlimited.
   * @param offset The offset into the ordering to start returning entries.  Defaults to 0.
   */
  def getAllDomainUsers(
    orderBy: Option[DomainUserField.Field],
    sortOrder: Option[SortOrder.Value],
    limit: Option[Int],
    offset: Option[Int]): Try[List[DomainUser]] = tryWithDb { db =>

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val baseQuery = s"SELECT * FROM User WHERE userType = 'normal' ORDER BY $order $sort"
    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(baseQuery, limit, offset))
    val result: JavaList[ODocument] = db.command(query).execute()

    result.asScala.toList.map { doc => doc.asDomainUser }
  }

  def searchUsersByFields(
    fields: List[DomainUserField.Field],
    searchString: String,
    orderBy: Option[DomainUserField.Field],
    sortOrder: Option[SortOrder.Value],
    limit: Option[Int],
    offset: Option[Int]): Try[List[DomainUser]] = tryWithDb { db =>

    val baseQuery = "SELECT * FROM User"
    val whereTerms = ListBuffer[String]()

    fields.foreach { field =>
      whereTerms += s"$field LIKE :searchString"
    }

    val whereClause = " WHERE userType = 'normal' AND (" + whereTerms.mkString(" OR ") + ")"

    val order = orderBy.getOrElse(DomainUserField.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val orderByClause = s" ORDER BY $order $sort"

    val pagedQuery = QueryUtil.buildPagedQuery(baseQuery + whereClause + orderByClause, limit, offset)
    val query = new OSQLSynchQuery[ODocument](pagedQuery)

    val params = Map("searchString" -> ("%" + searchString + "%"))
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    result.asScala.toList.map { doc => doc.asDomainUser }
  }

  /**
   * Set the password for an existing user by uid.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setDomainUserPassword(username: String, password: String): Try[UpdateResult] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM UserCredential WHERE user.username = :username AND user.userType = 'normal'")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        doc.field(Password, PasswordUtil.hashPassword(password))
        db.save(doc)
        UpdateSuccess
      case None => NotFound
    }
  }

  /**
   * Validated that the username and password combination are valid.
   *
   * @param username The username of the user to check the password for.
   * @param password The cleartext password of the user
   *
   * @return true if the username and passowrd match, false otherwise.
   */
  def validateCredentials(username: String, password: String): Try[Boolean] = tryWithDb { db =>
    val query = new OSQLSynchQuery[ODocument]("SELECT password, user.uid AS uid FROM UserCredential WHERE user.username = :username AND user.userType = 'normal'")
    val params = Map(Username -> username)
    val result: JavaList[ODocument] = db.command(query).execute(params.asJava)

    QueryUtil.enforceSingletonResultList(result) match {
      case Some(doc) =>
        val pwhash: String = doc.field(Password)
        PasswordUtil.checkPassword(password, pwhash)
      case None =>
        false
    }
  }
  
  def setLastLogin(username: String, userType: DomainUserType.Value, instant: Instant): Try[Unit] = tryWithDb { db =>
    val index = db.getMetadata.getIndexManager.getIndex(UsernameIndex)
    val key = new OCompositeKey(List(username, userType.toString.toLowerCase).asJava)
    if(index.contains(key)) {
      val record: ODocument = index.get(key).asInstanceOf[OIdentifiable].getRecord[ODocument]
      record.field(LastLogin, instant.toEpochMilli()).save()
    }
    Unit
  }

  def nextSessionId: Try[String] = tryWithDb { db =>
    val seq = db.getMetadata().getSequenceLibrary().getSequence(SessionSeq)
    Long.toString(seq.next(), 36)
  }
}

object DomainUserField extends Enumeration {
  type Field = Value
  val Username = Value("username")
  val FirstName = Value("firstName")
  val LastName = Value("lastName")
  val DisplayName = Value("displayName")
  val Email = Value("email")
}
