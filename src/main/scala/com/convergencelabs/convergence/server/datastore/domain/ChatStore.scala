/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.domain

import java.time.Instant
import java.util.{Date, Set => JavaSet}

import com.convergencelabs.convergence.server.datastore.{AbstractDatabasePersistence, DuplicateValueException, OrientDBUtil}
import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.{DomainUserId, DomainUserType}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.collection.JavaConverters.{asScalaSetConverter, seqAsJavaListConverter, setAsJavaSetConverter}
import scala.util.{Failure, Success, Try}

case class Chat(
  id:         String,
  chatType:   ChatType.Value,
  created:    Instant,
  membership: ChatMembership.Value,
  name:       String,
  topic:      String)

case class ChatInfo(
  id:              String,
  chatType:        ChatType.Value,
  created:         Instant,
  membership:      ChatMembership.Value,
  name:            String,
  topic:           String,
  lastEventNumber: Long,
  lastEventTime:   Instant,
  members:         Set[ChatMember])

sealed trait ChatEvent {
  val eventNumber: Long
  val id: String
  val user: DomainUserId
  val timestamp: Instant
}

case class ChatCreatedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  name:        String,
  topic:       String,
  members:     Set[DomainUserId]) extends ChatEvent

case class ChatMessageEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  message:     String) extends ChatEvent

case class ChatUserJoinedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant) extends ChatEvent

case class ChatUserLeftEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant) extends ChatEvent

case class ChatUserAddedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  userAdded:   DomainUserId) extends ChatEvent

case class ChatUserRemovedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  userRemoved: DomainUserId) extends ChatEvent

case class ChatNameChangedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  name:        String) extends ChatEvent

case class ChatTopicChangedEvent(
  eventNumber: Long,
  id:          String,
  user:        DomainUserId,
  timestamp:   Instant,
  topic:       String) extends ChatEvent

case class ChatMember(chatId: String, userId: DomainUserId, seen: Long)

object ChatMembership extends Enumeration {
  val Public, Private = Value

  def parse(s: String): ChatMembership.Value = values.find(_.toString.toLowerCase() == s.toLowerCase()) match {
    case Some(v) => v
    case None    => throw new IllegalArgumentException("Invalid ChatMembership string: " + s)
  }
}

object ChatType extends Enumeration {
  val Channel, Room, Direct = Value

  def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())

  def parse(s: String): ChatType.Value = values.find(_.toString.toLowerCase() == s.toLowerCase()) match {
    case Some(v) => v
    case None    => throw new IllegalArgumentException("Invalid ChatType string: " + s)
  }
}

object ChatStore {

  import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema._

  def chatTypeString(chatType: ChatType.Value): String = chatType match {
    case ChatType.Channel => "channel"
    case ChatType.Room    => "room"
    case ChatType.Direct  => "direct"
  }

  object Params {
    val Id = "id"
    val Type = "type"
    val Created = "created"
    val Private = "private"
    val Name = "name"
    val Topic = "topic"
    val Members = "members"

    val EventNo = "eventNo"
    val User = "user"
    val Timestamp = "timestamp"

    val Message = "message"
    val UserAdded = "userAdded"
    val UserRemoved = "userRemoved"

    val Seen = "seen"

    val Username = "username"
  }

  def docToChat(doc: ODocument): Chat = {
    val created: Date = doc.getProperty(Classes.Chat.Fields.Created)
    Chat(
      doc.getProperty(Classes.Chat.Fields.Id),
      ChatType.parse(doc.getProperty(Classes.Chat.Fields.Type)),
      created.toInstant,
      if (doc.getProperty(Classes.Chat.Fields.Private).asInstanceOf[Boolean]) {
        ChatMembership.Private
      } else {
        ChatMembership.Public
      },
      doc.getProperty(Classes.Chat.Fields.Name),
      doc.getProperty(Classes.Chat.Fields.Topic))
  }

  def chatToDoc(chat: Chat): ODocument = {
    val doc = new ODocument(Classes.Chat.ClassName)
    doc.setProperty(Classes.Chat.Fields.Id, chat.id)
    doc.setProperty(Classes.Chat.Fields.Type, chat.chatType.toString.toLowerCase)
    doc.setProperty(Classes.Chat.Fields.Created, Date.from(chat.created))
    doc.setProperty(Classes.Chat.Fields.Private, chat.membership == ChatMembership.Private)
    doc.setProperty(Classes.Chat.Fields.Name, chat.name)
    doc.setProperty(Classes.Chat.Fields.Topic, chat.topic)
    doc.setProperty(Classes.Chat.Fields.Members, new java.util.HashSet[ORID]())
    doc
  }

  def docToChatEvent(doc: ODocument): ChatEvent = {
    val eventNo: Long = doc.getProperty(Classes.ChatEvent.Fields.EventNo)
    val chatId = doc.eval("chat.id").asInstanceOf[String]
    val username = doc.eval("user.username").asInstanceOf[String]
    val userType = doc.eval("user.userType").asInstanceOf[String]
    val user = DomainUserId(DomainUserType.withName(userType), username)
    val timestamp: Date = doc.getProperty(Classes.ChatEvent.Fields.Timestamp)

    val className = doc.getClassName

    className match {
      case Classes.ChatCreatedEvent.ClassName =>
        val name: String = doc.getProperty(Classes.ChatCreatedEvent.Fields.Name)
        val topic: String = doc.getProperty(Classes.ChatCreatedEvent.Fields.Topic)
        val members: JavaSet[ODocument] = doc.getProperty(Classes.ChatCreatedEvent.Fields.Members)
        val userIds: Set[DomainUserId] = members.asScala.toSet.map { doc: ODocument =>
          val username = doc.getProperty(Classes.User.Fields.Username).asInstanceOf[String]
          val userType = doc.getProperty(Classes.User.Fields.UserType).asInstanceOf[String]
          DomainUserId(DomainUserType.withName(userType), username)
        }
        ChatCreatedEvent(eventNo, chatId, user, timestamp.toInstant, name, topic, userIds)
      case Classes.ChatMessageEvent.ClassName =>
        val message: String = doc.getProperty(Classes.ChatMessageEvent.Fields.Message)
        ChatMessageEvent(eventNo, chatId, user, timestamp.toInstant, message)
      case Classes.ChatUserJoinedEvent.ClassName =>
        ChatUserJoinedEvent(eventNo, chatId, user, timestamp.toInstant)
      case Classes.ChatUserLeftEvent.ClassName =>
        ChatUserLeftEvent(eventNo, chatId, user, timestamp.toInstant)
      case Classes.ChatUserAddedEvent.ClassName =>
        val userAdded = doc.eval("userAdded.username").asInstanceOf[String]
        val userType = doc.eval("userAdded.userType").asInstanceOf[String]
        ChatUserAddedEvent(eventNo, chatId, user, timestamp.toInstant, DomainUserId(DomainUserType.withName(userType), userAdded))
      case Classes.ChatUserRemovedEvent.ClassName =>
        val userRemoved = doc.eval("userRemoved.username").asInstanceOf[String]
        val userType = doc.eval("userRemoved.userType").asInstanceOf[String]
        ChatUserRemovedEvent(eventNo, chatId, user, timestamp.toInstant, DomainUserId(DomainUserType.withName(userType), userRemoved))
      case Classes.ChatTopicChangedEvent.ClassName =>
        val topic: String = doc.getProperty(Classes.ChatTopicChangedEvent.Fields.Topic)
        ChatTopicChangedEvent(eventNo, chatId, user, timestamp.toInstant, topic)
      case Classes.ChatNameChangedEvent.ClassName =>
        val name: String = doc.getProperty(Classes.ChatNameChangedEvent.Fields.Name)
        ChatNameChangedEvent(eventNo, chatId, user, timestamp.toInstant, name)
      case _ =>
        throw new IllegalArgumentException(s"Unknown Chat Event class name: ${className}")
    }
  }

  def toChatInfo(doc: ODocument): ChatInfo = {
    val id: String = doc.getProperty(Classes.Chat.Fields.Id)
    val chatType: String = doc.getProperty(Classes.Chat.Fields.Type)
    val created: Instant = doc.getProperty(Classes.Chat.Fields.Created).asInstanceOf[Date].toInstant()
    val isPrivate: Boolean = doc.getProperty(Classes.Chat.Fields.Private)
    val name: String = doc.getProperty(Classes.Chat.Fields.Name)
    val topic: String = doc.getProperty(Classes.Chat.Fields.Topic)
    val members: JavaSet[OIdentifiable] = doc.getProperty(Classes.Chat.Fields.Members)
    val chatMemebers: Set[ChatMember] = members.asScala.map(member => {
      val doc = member.getRecord.asInstanceOf[ODocument]
      val username = doc.eval("user.username").asInstanceOf[String]
      val userType = doc.eval("user.userType").asInstanceOf[String]
      val userId = DomainUserId(DomainUserType.withName(userType), username)
      val seen = doc.getProperty("seen").asInstanceOf[Long]
      ChatMember(id, userId, seen)
    }).toSet
    val lastEventNo: Long = doc.getProperty(Classes.ChatEvent.Fields.EventNo)
    val lastEventTime: Instant = doc.getProperty(Classes.ChatEvent.Fields.Timestamp).asInstanceOf[Date].toInstant()
    ChatInfo(
      id,
      ChatType.parse(chatType),
      created,
      if (isPrivate) {
        ChatMembership.Private
      } else {
        ChatMembership.Public
      },
      name,
      topic,
      lastEventNo,
      lastEventTime,
      chatMemebers)
  }

  def getChatRid(chatId: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Chat.Indices.Id, chatId)
  }
}

class ChatStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {
  import ChatStore._
  import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema._

  def findChats(types: Option[Set[String]], filter: Option[String], offset: Option[Int], limit: Option[Int]): Try[List[ChatInfo]] = withDb { db =>
    val chatTypes = types.getOrElse(Set(ChatType.Channel.toString.toLowerCase(), ChatType.Room.toString.toLowerCase()))

    val (whereClause, whereParams) = filter match {
      case Some(filter) =>
        val where = "chat.type IN :chatTypes AND (chat.topic.toLowerCase() LIKE :term OR chat.name.toLowerCase() LIKE :term OR chat.id.toLowerCase() LIKE :term)"
        val params = Map("chatTypes" -> chatTypes.asJava, "term" -> s"%${filter.toLowerCase}%")
        (where, params)
      case None =>
        val where = "chat.type IN :chatTypes"
        val params = Map("chatTypes" -> chatTypes.asJava)
        (where, params)
    }

    val baseQuery = s"""|SELECT 
       |  max(eventNo) as eventNo, 
       |  max(timestamp) as timestamp,
       |  chat.id as id, 
       |  chat.type as type, 
       |  chat.created as created,
       |  chat.private as private,
       |  chat.name as name, 
       |  chat.topic as topic,
       |  chat.members as members
       |FROM
       |  ChatEvent 
       |WHERE
       |  ${whereClause}
       |GROUP BY (chat)""".stripMargin

    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)

    OrientDBUtil.queryAndMap(db, query, whereParams) { doc =>
      toChatInfo(doc)
    }
  }

  private[this] val GetChatInfosQuery =
    """|SELECT 
       |  max(eventNo) as eventNo, 
       |  max(timestamp) as timestamp,
       |  chat.id as id, 
       |  chat.type as type, 
       |  chat.created as created,
       |  chat.private as private,
       |  chat.name as name, 
       |  chat.topic as topic,
       |  chat.members as members
       |FROM
       |  ChatEvent 
       |WHERE
       |  chat.id IN :chatIds
       |GROUP BY (chat)""".stripMargin

  def getChatInfo(chatIds: List[String]): Try[List[ChatInfo]] = withDb { db =>
    val params = Map("chatIds" -> chatIds.asJava)
    OrientDBUtil.queryAndMap(db, GetChatInfosQuery, params) { doc =>
      toChatInfo(doc)
    }
  }

  private[this] val GetChatInfoQuery =
    """|SELECT 
       |  max(eventNo) as eventNo, 
       |  max(timestamp) as timestamp,
       |  chat.id as id, 
       |  chat.type as type, 
       |  chat.created as created,
       |  chat.private as private,
       |  chat.name as name, 
       |  chat.topic as topic,
       |  chat.members as members
       |FROM
       |  ChatEvent 
       |WHERE
       |  chat.id == :chatId""".stripMargin
  def getChatInfo(chatId: String): Try[ChatInfo] = withDb { db =>
    val params = Map("chatId" -> chatId)
    OrientDBUtil.getDocument(db, GetChatInfoQuery, params).map(toChatInfo)
  }

  def getChat(chatId: String): Try[Chat] = withDb { db =>
    ChatStore.getChatRid(chatId, db)
      .flatMap(rid => Try(rid.getRecord[ODocument]))
      .map(docToChat)
  }

  def createChat(
    id:           Option[String],
    chatType:     ChatType.Value,
    creationTime: Instant,
    membership:   ChatMembership.Value,
    name:         String,
    topic:        String,
    members:      Option[Set[DomainUserId]],
    createdBy:    DomainUserId): Try[String] = tryWithDb { db =>
    // FIXME: return failure if addAllChatMembers fails
    db.begin()
    val chatId = id.getOrElse {
      "#" + OrientDBUtil.sequenceNext(db, DomainSchema.Sequences.ChatId).get
    }
    val doc = chatToDoc(Chat(chatId, chatType, creationTime, membership, name, topic))
    db.save(doc)
    db.commit()

    members.foreach { userId =>
      addAllChatMembers(chatId, userId, None).get
    }

    // FIXME why is this needed? It seems like the above might put another db into the active thread.
    db.activateOnCurrentThread()

    db.commit()
    this.addChatCreatedEvent(ChatCreatedEvent(0, chatId, createdBy, creationTime, name, topic, members.getOrElse(Set()))).get

    db.activateOnCurrentThread()
    db.commit()
    chatId
  } recoverWith {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case DomainSchema.Classes.Chat.Indices.Id =>
          Failure(DuplicateValueException(DomainSchema.Classes.Chat.Fields.Id))
        case _ =>
          Failure(e)
      }
  }

  def getDirectChatInfoByUsers(userIds: Set[DomainUserId]): Try[Option[ChatInfo]] = withDb { db =>
    // TODO is there a better way to do this using ChatMember class, like maybe with
    // a group by / count WHERE'd on the Channel Link?

    DomainUserStore.getDomainUsersRids(userIds.toList, db).flatMap { userRids =>
      val query = """
       |SELECT 
       |  id 
       |FROM 
       |  Chat
       |WHERE 
       |  members CONTAINSALL (user IN :users) AND
       |  members.size() = :size AND 
       |  type='direct'""".stripMargin

      // TODO is there a way to do this in one step not two?
      val params = Map("users" -> userRids.asJava, "size" -> userRids.size)
      OrientDBUtil
        .findDocument(db, query, params)
        .flatMap {
          case Some(doc) =>
            val id: String = doc.getProperty("id")
            this.getChatInfo(id).map(Some(_))
          case None =>
            Success(None)
        }
    }
  }

  def getJoinedChats(userId: DomainUserId): Try[List[ChatInfo]] = withDb { db =>
    val query =
      """
       |SELECT 
       |  chat.id as chatId
       |FROM 
       |  ChatMember
       |WHERE 
       |  user.username = :username AND 
       |  user.userType = :userType AND
       |  chat.type='channel'""".stripMargin
    val params = Map("username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil
      .query(db, query, params)
      .flatMap(docs => getChatInfo(docs.map(_.getProperty("chatId").asInstanceOf[String])))
  }

  def updateChat(chatId: String, name: Option[String], topic: Option[String]): Try[Unit] = withDb { db =>
    ChatStore.getChatRid(chatId, db)
      .flatMap { chatRid =>
        Try {
          val doc = chatRid.getRecord[ODocument]
          name.foreach(doc.field(Classes.Chat.Fields.Name, _))
          topic.foreach(doc.field(Classes.Chat.Fields.Topic, _))
          doc.save()
          ()
        }
      }
  }

  private[this] val DeleteChatEventsCommand = "DELETE FROM ChatEvent WHERE chat = (SELECT FROM Chat WHERE id = :chatId)"
  private[this] val DeleteChatMembersCommand = "DELETE FROM ChatMember WHERE chat = (SELECT FROM Chat WHERE id = :chatId)"
  
  def removeChat(id: String): Try[Unit] = withDb { db =>
    val params = Map("chatId" -> id)
    db.begin()
    for {
      _ <- OrientDBUtil.commandReturningCount(db, DeleteChatEventsCommand, params)
      _ <- OrientDBUtil.commandReturningCount(db, DeleteChatMembersCommand, params)
      _ <- OrientDBUtil.deleteFromSingleValueIndex(db, Classes.Chat.Indices.Id, id)
//      chatRid <- ChatStore.getChatRid(id, db)
//      _ <- Try(chatRid.getRecord[ODocument].delete())
      _ <- Try(db.commit())
    } yield(())
  }

  // TODO: All of the events are very similar, need to abstract some of each of these methods

  def addChatCreatedEvent(event: ChatCreatedEvent): Try[Unit] = withDb { db =>
    val ChatCreatedEvent(eventNo, chatId, user, timestamp, name, topic, members) = event

    val memberList = members.toList
    DomainUserStore.getDomainUsersRids(memberList, db)
      .flatMap { users =>
        val query =
          """INSERT INTO ChatCreatedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  name = :name,
        |  topic = :topic,
        |  members = :members""".stripMargin
        val params = Map(
          "eventNo" -> eventNo,
          "chatId" -> chatId,
          "username" -> user.username,
          "userType" -> user.userType.toString.toLowerCase,
          "timestamp" -> Date.from(timestamp),
          "name" -> name,
          "topic" -> topic,
          "members" -> users.asJava)

        // FIXME we need a way to make sure that the event actually got saved. The result should be the
        // created ODocument we need to make sure.
        OrientDBUtil
          .commandReturningCount(db, query, params)
          .map(_ => ())
      }
  }

  def addChatMessageEvent(event: ChatMessageEvent): Try[Unit] = withDb { db =>
    val ChatMessageEvent(eventNo, chatId, user, timestamp, message) = event
    val query =
      """INSERT INTO ChatMessageEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  message = :message""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp),
      "message" -> message)
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatUserJoinedEvent(event: ChatUserJoinedEvent): Try[Unit] = withDb { db =>
    val ChatUserJoinedEvent(eventNo, chatId, user, timestamp) = event
    val query =
      """INSERT INTO ChatUserJoinedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp))
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatUserLeftEvent(event: ChatUserLeftEvent): Try[Unit] = withDb { db =>
    val ChatUserLeftEvent(eventNo, chatId, user, timestamp) = event
    val query =
      """INSERT INTO ChatUserLeftEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp))
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatUserAddedEvent(event: ChatUserAddedEvent): Try[Unit] = withDb { db =>
    val ChatUserAddedEvent(eventNo, chatId, user, timestamp, userAdded) = event
    val query =
      """INSERT INTO ChatUserAddedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  userAdded = (SELECT FROM User WHERE username = :addedUsername AND userType = :addedUserType)""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp),
      "addedUsername" -> userAdded.username,
      "addedUserType" -> userAdded.userType.toString.toLowerCase)
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatUserRemovedEvent(event: ChatUserRemovedEvent): Try[Unit] = withDb { db =>
    val ChatUserRemovedEvent(eventNo, chatId, user, timestamp, userRemoved) = event
    val query =
      """INSERT INTO ChatUserRemovedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  userRemoved = (SELECT FROM User WHERE username = :removedUsername AND userType = :removedUserType)""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp),
      "removedUsername" -> userRemoved.username,
      "removedUserType" -> userRemoved.userType.toString.toLowerCase)
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatNameChangedEvent(event: ChatNameChangedEvent): Try[Unit] = withDb { db =>
    val ChatNameChangedEvent(eventNo, chatId, user, timestamp, name) = event
    val query =
      """INSERT INTO ChatNameChangedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  name = :name""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp),
      "name" -> name)
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def addChatTopicChangedEvent(event: ChatTopicChangedEvent): Try[Unit] = withDb { db =>
    val ChatTopicChangedEvent(eventNo, chatId, user, timestamp, topic) = event
    val query =
      """INSERT INTO ChatTopicChangedEvent SET
        |  eventNo = :eventNo,
        |  chat = (SELECT FROM Chat WHERE id = :chatId),
        |  user = (SELECT FROM User WHERE username = :username AND userType = :userType),
        |  timestamp = :timestamp,
        |  topic = :topic""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "chatId" -> chatId,
      "username" -> user.username,
      "userType" -> user.userType.toString.toLowerCase,
      "timestamp" -> Date.from(timestamp),
      "topic" -> topic)
    OrientDBUtil
      .commandReturningCount(db, query, params)
      .map(_ => ())
  }

  def getChatMembers(chatId: String): Try[Set[DomainUserId]] = withDb { db =>
    val query = "SELECT user.username as username, user.userType as userType FROM ChatMember WHERE chat.id = :chatId"
    val params = Map("chatId" -> chatId)

    OrientDBUtil
      .queryAndMap(db, query, params)(d => DomainUserId(d.getProperty("userType").asInstanceOf[String], d.getProperty("username")))
      .map(_.toSet)
  }

  def addAllChatMembers(chatId: String, userIds: Set[DomainUserId], seen: Option[Long]): Try[Unit] = withDb { db =>
    val seenVal = seen.getOrElse(0)
    // FIXME we should do all of this in a transaction so they all succeed or fail
    ChatStore.getChatRid(chatId, db)
      .flatMap { chatRid =>
        val results = userIds.map { userId =>
          DomainUserStore.getUserRid(userId, db)
            .flatMap(userRid => addChatMember(db, chatRid, userRid, seen))
        }

        Try(results.map(_.get)).map(_ => ())
      }
  }

  def addChatMember(chatId: String, userId: DomainUserId, seen: Option[Long]): Try[Unit] = withDb { db =>
    for {
      chatRid <- ChatStore.getChatRid(chatId, db)
      userRid <- DomainUserStore.getUserRid(userId, db)
      _ <- addChatMember(db, chatRid, userRid, seen)
    } yield (())
  }

  private[this] def addChatMember(db: ODatabaseDocument, chatRid: ORID, userRid: ORID, seen: Option[Long]): Try[Unit] = Try {
    val doc = db.newElement(Classes.ChatMember.ClassName)
    doc.setProperty(Classes.ChatMember.Fields.Chat, chatRid)
    doc.setProperty(Classes.ChatMember.Fields.User, userRid)
    doc.setProperty(Classes.ChatMember.Fields.Seen, seen.getOrElse(0))
    db.save(doc)

    val chatDoc = chatRid.getRecord[ODocument]
    val members: JavaSet[ORID] = chatDoc.field(Classes.Chat.Fields.Members)
    members.add(doc.getIdentity)
    chatDoc.field(Classes.Chat.Fields.Members, members)
    chatDoc.save()
  }

  def removeChatMember(chatId: String, userId: DomainUserId): Try[Unit] = withDb { db =>
    val script = """
      |BEGIN;
      |LET chatId = :chatId;
      |LET chat = (SELECT FROM Chat WHERE id = $chatId);
      |LET user = (SELECT FROM User WHERE username = :username AND userType = :userType);
      |LET member = (SELECT FROM ChatMember WHERE chat = $chat AND user = $user);
      |UPDATE Chat REMOVE members = $member WHERE id = $chatId;
      |DELETE FROM (SELECT expand($member));
      |COMMIT;
      """.stripMargin
    
    val params = Map("chatId" -> chatId, "username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil.execute(db, script, params).map(_ => ())
  }

  def markSeen(chatId: String, userId: DomainUserId, seen: Long): Try[Unit] = tryWithDb { db =>
    getChatMemberRid(chatId, userId).flatMap { memberRid =>
      Try {
        val doc = memberRid.getRecord[ODocument]
        doc.field(Classes.ChatMember.Fields.Seen, seen)
        doc.save()
        ()
      }
    }
  }

  def getChatEvents(
    chatId:     String,
    eventTypes: Option[List[String]],
    startEvent: Option[Long],
    limit:      Option[Int],
    forward:    Option[Boolean]): Try[List[ChatEvent]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]("chatId" -> chatId)

    val eventTypesClause = eventTypes.getOrElse(List()) match {
      case Nil =>
        ""
      case types =>
        params("types") = types.map(getClassName(_)).filter(_.isDefined).map(_.get).asJava
        "AND @class IN :types"
    }

    val fwd = forward.getOrElse(false)
    val eventNoClause = startEvent map { eventNo =>
      val operator = if (fwd) {
        ">="
      } else {
        "<="
      }
      params("startEventNo") = eventNo
      s" AND eventNo $operator :startEventNo"
    } getOrElse ""

    val orderBy = if (fwd) {
      "ASC"
    } else {
      "DESC"
    }

    val baseQuery = s"SELECT FROM ChatEvent WHERE chat.id = :chatId $eventTypesClause $eventNoClause ORDER BY eventNo $orderBy"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, Some(limit.getOrElse(50)), Some(0))

    OrientDBUtil
      .query(db, query, params.toMap)
      .map(_.map(docToChatEvent).sortWith((e1, e2) => e1.eventNumber < e2.eventNumber))
  }

  def getChatRid(channelId: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Chat.Indices.Id, channelId)
  }

  def getChatMemberRid(channelId: String, userId: DomainUserId): Try[ORID] = withDb { db =>
    val channelRID = ChatStore.getChatRid(channelId, db).get
    val userRID = DomainUserStore.getUserRid(userId, db).get
    val key = List(channelRID, userRID)
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.ChatMember.Indices.Chat_User, key)
  }

  def getClassName: PartialFunction[String, Option[String]] = {
    case "message"       => Some(Classes.ChatMessageEvent.ClassName)
    case "created"       => Some(Classes.ChatCreatedEvent.ClassName)
    case "user_joined"   => Some(Classes.ChatUserJoinedEvent.ClassName)
    case "user_left"     => Some(Classes.ChatUserLeftEvent.ClassName)
    case "user_added"    => Some(Classes.ChatUserAddedEvent.ClassName)
    case "user_removed"  => Some(Classes.ChatUserRemovedEvent.ClassName)
    case "name_changed"  => Some(Classes.ChatNameChangedEvent.ClassName)
    case "topic_changed" => Some(Classes.ChatTopicChangedEvent.ClassName)
    case _               => None
  }
}
