package com.convergencelabs.server.datastore.domain

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.immutable.HashMap

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.datastore.SortOrder
import com.lambdaworks.crypto.SCryptUtil
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

object DomainUserStore {

  object Fields {
    val Uid = "uid"
    val Username = "username"
    val FirstName = "firstName"
    val LastName = "lastName"
    val Email = "email"
  }

  private def docToDomainUser(doc: ODocument): DomainUser = {
    DomainUser(
      doc.field(Fields.Uid),
      doc.field(Fields.Username),
      doc.field(Fields.FirstName),
      doc.field(Fields.LastName),
      doc.field(Fields.Email))
  }

  private def domainUserToDoc(o: DomainUser): ODocument = {
    val doc = new ODocument("User")
    doc.field(Fields.Uid, o.uid)
    doc.field(Fields.Username, o.username)
    doc.field(Fields.FirstName, o.firstName)
    doc.field(Fields.LastName, o.lastName)
    doc.field(Fields.Email, o.email)
  }
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
class DomainUserStore private[domain] (private[this] val dbPool: OPartitionedDatabasePool) {

  private[this] implicit val formats = Serialization.formats(NoTypeHints)

  /**
   * Creates a new domain user in the system, and optionally set a password.
   * Note the uid as well as the username of the user must be unique among all
   * users in this domain.
   *
   * @param domainUser The user to add to the system.
   *
   * @param password An optional password if internal authentication is to
   * be used for this user.
   */
  def createDomainUser(domainUser: DomainUser, password: Option[String]): Unit = {
    val db = dbPool.acquire()
    val userDoc = DomainUserStore.domainUserToDoc(domainUser)
    db.save(userDoc)

    val pwDoc = db.newInstance("UserCredential")
    pwDoc.field("user", userDoc, OType.LINK) // FIXME verify this creates a link and now a new doc.

    val hash = password match {
      case Some(pass) => PasswordUtil.hashPassword(pass)
      case None => null
    }

    pwDoc.field("password", hash)
    db.save(pwDoc)
    db.close()
  }

  /**
   * Deletes a single domain user by uid.
   *
   * @param the uid of the user to delete.
   */
  def deleteDomainUser(uid: String): Unit = {
    val db = dbPool.acquire()
    val command = new OCommandSQL("DELETE FROM User WHERE uid = :uid")
    val params = HashMap("uid" -> uid)
    db.command(command).execute(params.asJava)
    db.close()
  }

  /**
   * Updates a DomainUser with new information.  The uid of the domain user argument must
   * correspond to an existing user in the database.
   *
   * @param domainUser The user to update.
   */
  def updateDomainUser(domainUser: DomainUser): Unit = {
    val db = dbPool.acquire()
    val updatedDoc = db.newInstance("user")
    updatedDoc.fromJSON(write(domainUser))

    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE uid = :uid")
    val params = HashMap("uid" -> domainUser.uid)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    result.asScala.toList match {
      case doc :: Nil => {
        doc.merge(updatedDoc, false, false)
        db.save(doc)
      }
      case _ => ???
    }
    db.close()
  }

  /**
   * Gets a single domain user by uid.
   *
   * @param uid The uid of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified uid exists, or None if no such user exists.
   */
  def getDomainUserByUid(uid: String): Option[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE uid = :uid")
    val params = HashMap("uid" -> uid)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)

    db.close()

    result.asScala.toList match {
      case doc :: rest => Some(DomainUserStore.docToDomainUser(doc))
      case Nil => None
    }
  }

  /**
   * Gets a list of domain users matching any of a list of uids.
   *
   * @param uids The list of uids of the users to retrieve.
   *
   * @return A list of users matching the list of supplied uids.
   */
  def getDomainUsersByUids(uids: List[String]): List[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE uid in :uids")
    val params = HashMap("uids" -> uids)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()
    result.asScala.toList.map { doc => DomainUserStore.docToDomainUser(doc) }
  }

  /**
   * Gets a single domain user by usernam.
   *
   * @param username The uid of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified username exists, or None if no such user exists.
   */
  def getDomainUserByUsername(username: String): Option[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = HashMap("username" -> username)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList match {
      case doc :: Nil => Some(DomainUserStore.docToDomainUser(doc))
      case _ => None
    }
  }

  /**
   * Gets a list of domain users matching any of a list of usernames.
   *
   * @param uids The list of usernames of the users to retrieve.
   *
   * @return A list of users matching the list of supplied usernames.
   */
  def getDomainUsersByUsername(usernames: List[String]): List[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username in :usernames")
    val params = HashMap("usernames" -> usernames)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList.map { doc => DomainUserStore.docToDomainUser(doc) }
  }

  /**
   * Gets a single domain user by email.
   *
   * @param email The email of the user to retrieve.
   *
   * @return Some(DomainUser) if a user with the specified email exists, or None if no such user exists.
   */
  def getDomainUserByEmail(email: String): Option[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE email = :email")
    val params = HashMap("email" -> email)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList match {
      case doc :: Nil => Some(DomainUserStore.docToDomainUser(doc))
      case _ => None
    }
  }

  /**
   * Gets a list of domain users matching any of a list of emails.
   *
   * @param uids The list of emails of the users to retrieve.
   *
   * @return A list of users matching the list of supplied emails.
   */
  def getDomainUsersByEmail(emails: List[String]): List[DomainUser] = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE email in :emails")
    val params = HashMap("emails" -> emails)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList.map { doc => DomainUserStore.docToDomainUser(doc) }
  }

  /**
   * Checks to see if a given username exists in the system.
   *
   * @param username The username to check existence for.
   *
   * @return true if the user exists, false otherwise.
   */
  def domainUserExists(username: String): Boolean = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT FROM User WHERE username = :username")
    val params = HashMap("username" -> username)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

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
  def getAllDomainUsers(orderBy: Option[DomainUserOrder.Order], sortOrder: Option[SortOrder.Value], limit: Option[Int], offset: Option[Int]): List[DomainUser] = {
    val db = dbPool.acquire()
    val order = orderBy.getOrElse(DomainUserOrder.Username)
    val sort = sortOrder.getOrElse(SortOrder.Descending)
    val baseQuery = s"SELECT * FROM User ORDER BY $order $sort"
    val query = new OSQLSynchQuery[ODocument](QueryUtil.buildPagedQuery(baseQuery, limit, offset))
    val result: java.util.List[ODocument] = db.command(query).execute()
    db.close()

    result.asScala.toList.map { doc => DomainUserStore.docToDomainUser(doc) }
  }

  /**
   * Set the password for an existing user by username.
   *
   * @param username The unique username of the user.
   * @param password The new password to use for internal authentication
   */
  def setDomainUserPassword(username: String, password: String): Unit = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT * FROM UserCredential WHERE user.username = :username")
    val params = HashMap("username" -> username)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList match {
      case doc :: Nil => {
        doc.field("password", SCryptUtil.scrypt(password, 16384, 8, 1))
        db.save(doc)
      }
      case _ => ??? // FIXME We did not find a user to update.
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
  def validateCredentials(username: String, password: String): Boolean = {
    val db = dbPool.acquire()
    val query = new OSQLSynchQuery[ODocument]("SELECT password FROM UserCredential WHERE user.username = :username")
    val params = HashMap("username" -> username)
    val result: java.util.List[ODocument] = db.command(query).execute(params.asJava)
    db.close()

    result.asScala.toList match {
      case doc :: Nil => {
        val pwhash: String = doc.field("password")
        PasswordUtil.checkPassword(password, pwhash)
      }
      case _ => false
    }
  }

}

object DomainUserOrder extends Enumeration {
  type Order = Value
  val Username = Value("username")
  val FirstName = Value("firstName")
  val LastName = Value("lastName")
}

case class DomainUser(uid: String, username: String, firstName: String, lastName: String, email: String)