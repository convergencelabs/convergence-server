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

package com.convergencelabs.convergence.server.backend.datastore.domain.chat

import java.time.Instant
import java.util.{Date, Set => JavaSet}

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, DuplicateValueException, EntityNotFoundException, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.PagedChatEvents
import com.convergencelabs.convergence.server.model.domain.chat
import com.convergencelabs.convergence.server.model.domain.chat._
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import grizzled.slf4j.Logging

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ChatStore(dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  import ChatStore._
  import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema._

  def searchChats(searchTerm: Option[String],
                  searchFields: Option[Set[String]],
                  types: Option[Set[ChatType.Value]],
                  membership: Option[ChatMembership.Value],
                  offset: QueryOffset,
                  limit: QueryLimit): Try[PagedData[ChatState]] = withDb { db =>
    val chatTypes: Set[String] = types.getOrElse(Set(ChatType.Channel, ChatType.Room, ChatType.Direct)).map(_.toString.toLowerCase)

    val whereClauses = mutable.ListBuffer("chat.type IN :chatTypes")
    val whereParams: mutable.Map[String, Any] = mutable.Map("chatTypes" -> chatTypes.asJava)

    // If we have a term match it to the fields.
    searchTerm.foreach { term =>
      whereClauses += searchFields.getOrElse(Set("topic", "name", "id")).map(field => {
        s"chat.$field.toLowerCase() LIKE :term"
      }).mkString(" OR ")
      whereParams += ("term" -> s"%${term.toLowerCase}%")
    }

    // Filter on the membership
    membership.foreach { m =>
      whereClauses += "chat.membership = :membership"
      whereParams += ("membership" -> m)
    }
    val whereClause = whereClauses.mkString(" AND ")

    val countQuery =
      s"""|SELECT
          |  count(*) as count
          |FROM (
          |  SELECT
          |    distinct(chat)
          |  FROM
          |    ChatEvent
          |  WHERE
          |    $whereClause
          |)""".stripMargin

    val baseQuery =
      s"""|SELECT
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
          |  $whereClause
          |GROUP BY (chat)
          |ORDER BY chat""".stripMargin
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit, offset)

    val params = whereParams.toMap
    for {
      count <- OrientDBUtil.getDocument(db, countQuery, params).map(doc => doc.getProperty("count").asInstanceOf[Long])
      data <- OrientDBUtil.queryAndMap(db, query, params) { doc => toChatState(doc) }
    } yield {
      PagedData(data, offset.getOrZero, count)
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
       |GROUP BY (chat)
       |ORDER BY chat""".stripMargin

  def getChatState(chatIds: List[String]): Try[List[ChatState]] = withDb { db =>
    val params = Map("chatIds" -> chatIds.asJava)
    OrientDBUtil.queryAndMap(db, GetChatInfosQuery, params) { doc =>
      toChatState(doc)
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

  def getChatState(chatId: String): Try[ChatState] = withDb { db =>
    val params = Map("chatId" -> chatId)
    OrientDBUtil
      .getDocument(db, GetChatInfoQuery, params)
      .map(toChatState)
      .recoverWith {
        case _: EntityNotFoundException =>
          Failure(EntityNotFoundException(s"A chat with id '$chatId' does not exist", Some(chatId)))
      }
  }

  def findChatInfo(chatId: String): Try[Option[ChatState]] = withDb { db =>
    val params = Map("chatId" -> chatId)
    OrientDBUtil.findDocumentAndMap(db, GetChatInfoQuery, params) {
      toChatState
    }
  }

  val DirectChatPrefix = "direct:"

  def createChat(id: Option[String],
                 chatType: ChatType.Value,
                 creationTime: Instant,
                 membership: ChatMembership.Value,
                 name: String,
                 topic: String,
                 members: Option[Set[DomainUserId]],
                 createdBy: DomainUserId): Try[String] = tryWithDb { db =>

    db.begin()

    val chatId = id match {
      case Some(DirectChatPrefix) =>
        throw new IllegalArgumentException("chatId can not start with 'direct:'")
      case Some(id) =>
        id
      case None =>
        if (chatType == ChatType.Direct) {
          DirectChatPrefix + OrientDBUtil.sequenceNext(db, DomainSchema.Sequences.ChatId).get
        } else {
          throw new IllegalArgumentException("chatId must be specified for channels and rooms'")
        }
    }

    val doc = chatToDoc(CreateChat(chatId, chatType, creationTime, membership, name, topic))
    db.save(doc)
    db.commit()

    members.foreach { userIds =>
      addAllChatMembers(chatId, userIds, None, db).get
    }

    db.commit()

    val event = ChatCreatedEvent(0, chatId, createdBy, creationTime, name, topic, members.getOrElse(Set()))
    addChatCreatedEvent(event, db).get

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

  def getDirectChatInfoByUsers(userIds: Set[DomainUserId]): Try[Option[ChatState]] = withDb { db =>
    // TODO is there a better way to do this using ChatMember class, like maybe with
    // a group by / count WHERE'd on the Channel Link?

    DomainUserStore.getDomainUsersRids(userIds.toList, db).flatMap { userRids =>
      val query =
        """
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
            this.getChatState(id).map(Some(_))
          case None =>
            Success(None)
        }
    }
  }

  def getJoinedChannels(userId: DomainUserId): Try[Set[ChatState]] = withDb { db =>
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
      .flatMap(docs => getChatState(docs.map(_.getProperty("chatId").asInstanceOf[String]))
        .map(_.toSet))
  }

  def updateChat(chatId: String, name: Option[String], topic: Option[String], db: ODatabaseDocument): Try[Unit] = {
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
    (for {
      _ <- Try(db.begin())
      _ <- OrientDBUtil.commandReturningCount(db, DeleteChatEventsCommand, params)
      _ <- OrientDBUtil.commandReturningCount(db, DeleteChatMembersCommand, params)
      _ <- OrientDBUtil.deleteFromSingleValueIndex(db, Classes.Chat.Indices.Id, id)
      _ <- Try(db.commit())
    } yield ())
      .recoverWith(this.rollback(db))
  }

  // TODO: All of the events are very similar, need to abstract some of each of these methods

  def addChatCreatedEvent(event: ChatCreatedEvent, db: ODatabaseDocument): Try[Unit] = {
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

  def addChatUserJoinedEvent(event: ChatUserJoinedEvent): Try[Unit] = withDbTransaction { db =>
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

    for {
      chatRid <- ChatStore.getChatRid(chatId, db)
      userRid <- DomainUserStore.getUserRid(user, db)
      _ <- addChatMember(db, chatRid, userRid, None)
      _ <- OrientDBUtil.commandReturningCount(db, query, params)
    } yield ()
  }

  def addChatUserLeftEvent(event: ChatUserLeftEvent): Try[Unit] = withDbTransaction { db =>
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

    for {
      _ <- removeChatMember(chatId, user, Some(db))
      _ <- OrientDBUtil.commandReturningCount(db, query, params)
    } yield ()
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

    // TODO add transaction
    for {
      _ <- addAllChatMembers(chatId, Set(userAdded), None, db)
      _ <- OrientDBUtil
        .commandReturningCount(db, query, params)
        .map(_ => ())
    } yield ()
  }

  def addChatUserRemovedEvent(event: ChatUserRemovedEvent): Try[Unit] = withDbTransaction { db =>
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

    for {
      _ <- removeChatMember(chatId, user, Some(db))
      _ <- OrientDBUtil.commandReturningCount(db, query, params)
    } yield ()
  }

  def addChatNameChangedEvent(event: ChatNameChangedEvent): Try[Unit] = withDbTransaction { db =>
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
    for {
      _ <- updateChat(event.id, Some(event.name), None, db)
      _ <- OrientDBUtil.commandReturningCount(db, query, params)
    } yield ()
  }

  def addChatTopicChangedEvent(event: ChatTopicChangedEvent): Try[Unit] = withDbTransaction { db =>
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

    for {
      _ <- updateChat(event.id, None, Some(event.topic), db)
      _ <- OrientDBUtil.commandReturningCount(db, query, params)
    } yield ()
  }

  def getChatMembers(chatId: String): Try[Set[DomainUserId]] = withDb { db =>
    val query = "SELECT user.username as username, user.userType as userType FROM ChatMember WHERE chat.id = :chatId"
    val params = Map("chatId" -> chatId)

    OrientDBUtil
      .queryAndMap(db, query, params)(d =>
        DomainUserId(d.getProperty("userType").asInstanceOf[String], d.getProperty("username")))
      .map(_.toSet)
  }

  def addAllChatMembers(chatId: String, userIds: Set[DomainUserId], seen: Option[Long], db: ODatabaseDocument): Try[Unit] = {
    for {
      chatRid <- ChatStore.getChatRid(chatId, db)
      userRids <- Try(userIds.map { userId =>
        DomainUserStore.findUserRid(userId, db).get match {
          case Some(rid) =>
            rid
          case None =>
            throw EntityNotFoundException("Could not find user to add to chat", Some(userId))
        }
      })
      _ <- Try {
        userRids.foreach { userRid =>
          addChatMember(db, chatRid, userRid, seen).get
        }
      }
    } yield ()
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

  def removeChatMember(chatId: String, userId: DomainUserId, db: Option[ODatabaseDocument] = None): Try[Unit] = withDb(db) { db =>
    val script =
      """
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

  def removeUserFromAllChats(userId: DomainUserId): Try[Unit] = withDb { db =>
    val script =
      """
        |BEGIN;
        |LET user = (SELECT FROM User WHERE username = :username AND userType = :userType);
        |UPDATE Chat REMOVE members = members[user IN $user] WHERE members CONTAINS (user IN $user);
        |DELETE FROM ChatMember WHERE user IN $user;
        |COMMIT;
      """.stripMargin
    val params = Map("username" -> userId.username, "userType" -> userId.userType.toString.toLowerCase)
    OrientDBUtil.execute(db, script, params).map(_ => ())
  }

  def markSeen(chatId: String, userId: DomainUserId, seen: Long): Try[Unit] = withDb { db =>
    getChatMemberRid(chatId, userId, Some(db)).flatMap { memberRid =>
      Try {
        val doc = memberRid.getRecord[ODocument]
        doc.field(Classes.ChatMember.Fields.Seen, seen)
        doc.save()
        ()
      }
    }
  }

  def getChatEvents(chatId: String,
                    eventTypes: Option[Set[String]],
                    startEvent: Option[Long],
                    offset: QueryOffset,
                    limit: QueryLimit,
                    forward: Option[Boolean],
                    messageFilter: Option[String]): Try[PagedChatEvents] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]("chatId" -> chatId)

    val eventTypesClause = eventTypes.getOrElse(Set()).toList match {
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

    val filterClause = messageFilter map { term =>
      params("filter") = s"%${term.toLowerCase}%"
      s" AND message.toLowerCase() LIKE :filter"
    } getOrElse ""

    val orderBy = if (fwd) {
      "ASC"
    } else {
      "DESC"
    }

    val baseQuery = s"SELECT FROM ChatEvent WHERE chat.id = :chatId $eventTypesClause $eventNoClause $filterClause ORDER BY eventNo $orderBy"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, limit.getOrElse(50), offset)

    val countQuery = s"SELECT count(*) as count FROM ChatEvent WHERE chat.id = :chatId $eventTypesClause $eventNoClause $filterClause"

    for {
      count <- OrientDBUtil.getDocument(db, countQuery, params.toMap).map(doc => doc.getProperty("count").asInstanceOf[Long])
      events <- OrientDBUtil
        .query(db, query, params.toMap)
        .map(_.map(docToChatEvent).sortWith((e1, e2) => {
          if (fwd) {
            e1.eventNumber < e2.eventNumber
          } else {
            e2.eventNumber < e1.eventNumber
          }
        }))
    } yield {
      PagedChatEvents(events, offset.getOrZero, count)
    }
  }

  private def getChatMemberRid(chatId: String, userId: DomainUserId, db: Option[ODatabaseDocument] = None): Try[ORID] = withDb(db) { db =>
    val channelRID = ChatStore.getChatRid(chatId, db).get
    val userRID = DomainUserStore.getUserRid(userId, db).get
    val key = List(channelRID, userRID)
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.ChatMember.Indices.Chat_User, key)
  }

  private def getClassName: PartialFunction[String, Option[String]] = {
    case "message" => Some(Classes.ChatMessageEvent.ClassName)
    case "created" => Some(Classes.ChatCreatedEvent.ClassName)
    case "user_joined" => Some(Classes.ChatUserJoinedEvent.ClassName)
    case "user_left" => Some(Classes.ChatUserLeftEvent.ClassName)
    case "user_added" => Some(Classes.ChatUserAddedEvent.ClassName)
    case "user_removed" => Some(Classes.ChatUserRemovedEvent.ClassName)
    case "name_changed" => Some(Classes.ChatNameChangedEvent.ClassName)
    case "topic_changed" => Some(Classes.ChatTopicChangedEvent.ClassName)
    case _ => None
  }
}


object ChatStore {

  import DomainSchema._

  private def chatToDoc(chat: CreateChat): ODocument = {
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

  private def docToChatEvent(doc: ODocument): ChatEvent = {
    val eventNo: Long = doc.getProperty(Classes.ChatEvent.Fields.EventNo)
    val chatId = doc.eval("chat.id").asInstanceOf[String]
    val user = extractUserId(doc)
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
        throw new IllegalArgumentException(s"Unknown Chat Event class name: $className")
    }
  }

  private def toChatState(doc: ODocument): ChatState = {
    val id: String = doc.getProperty(Classes.Chat.Fields.Id)
    val chatType: String = doc.getProperty(Classes.Chat.Fields.Type)
    val created: Instant = doc.getProperty(Classes.Chat.Fields.Created).asInstanceOf[Date].toInstant
    val isPrivate: Boolean = doc.getProperty(Classes.Chat.Fields.Private)
    val name: String = doc.getProperty(Classes.Chat.Fields.Name)
    val topic: String = doc.getProperty(Classes.Chat.Fields.Topic)
    val members: JavaSet[OIdentifiable] = doc.getProperty(Classes.Chat.Fields.Members)
    val chatMembers: Set[ChatMember] = members.asScala.map(member => {
      val doc = member.getRecord.asInstanceOf[ODocument]
      val userId = extractUserId(doc)
      val seen = doc.getProperty("seen").asInstanceOf[Long]
      chat.ChatMember(id, userId, seen)
    }).toSet
    val lastEventNo: Long = doc.getProperty(Classes.ChatEvent.Fields.EventNo)
    val lastEventTime: Instant = doc.getProperty(Classes.ChatEvent.Fields.Timestamp).asInstanceOf[Date].toInstant
    ChatState(
      id,
      ChatType.parse(chatType).get,
      created,
      if (isPrivate) {
        ChatMembership.Private
      } else {
        ChatMembership.Public
      },
      name,
      topic,
      lastEventTime,
      lastEventNo,
      chatMembers.map(m => (m.userId, m)).toMap)
  }

  private def extractUserId(doc: ODocument): DomainUserId = {
    val username = doc.eval("user.username").asInstanceOf[String]
    val userType = doc.eval("user.userType").asInstanceOf[String]
    DomainUserId(DomainUserType.withName(userType), username)
  }

  private[domain] def getChatRid(chatId: String, db: ODatabaseDocument): Try[ORID] = {
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Classes.Chat.Indices.Id, chatId)
  }
}
