package com.convergencelabs.server.datastore.domain

import java.util.ArrayList
import java.util.{ List => JavaList }
import java.util.{ Set => JavaSet }

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConverters.asScalaSetConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.QueryUtil
import com.orientechnologies.orient.core.db.record.OTrackedList
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.convergencelabs.server.datastore.domain.ChatChannelStore._

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.EntityNotFoundException
import java.time.Instant
import java.util.Date
import java.util.HashSet

case class ChatChannel(
  id: String,
  channelType: String,
  created: Instant,
  isPrivate: Boolean,
  name: String,
  topic: String)

sealed trait ChatChannelEvent

case class ChatMessageEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant,
  message: String) extends ChatChannelEvent

case class ChatUserJoinedEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant) extends ChatChannelEvent

case class ChatUserLeftEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant) extends ChatChannelEvent

case class ChatUserAddedEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant,
  userAdded: String) extends ChatChannelEvent

case class ChatUserRemovedEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant,
  userRemoved: String) extends ChatChannelEvent

case class ChatNameChangedEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant,
  name: String) extends ChatChannelEvent

case class ChatTopicChangedEvent(
  eventNo: Long,
  channel: String,
  user: String,
  timestamp: Instant,
  topic: String) extends ChatChannelEvent

case class ChatChannelMember(channel: String, user: String, seen: Long)

object ChatChannelStore {

  object Classes {
    val ChatChannel = "ChatChannel"
    val ChatChannelEvent = "ChatChannelEvent"
    val ChatMessageEvent = "ChatMessageEvent"
    val ChatUserJoinedEvent = "ChatUserJoinedEvent"
    val ChatUserLeftEvent = "ChatUserLeftEvent"
    val ChatUserAddedEvent = "ChatUserAddedEvent"
    val ChatUserRemovedEvent = "ChatUserRemovedEvent"
    val ChatNameChangedEvent = "ChatNameChangedEvent"
    val ChatTopicChangedEvent = "ChatTopicChangedEvent"
    val ChatChannelMember = "ChatChannelMember"
  }

  object Indexes {
    val ChatChannel_Id = "ChatChannel.id"
    val ChatChannel_Name_Type = "ChatChannel.name_type"

    val ChatChannelEvent_EventNo_Channel = "ChatChannelEvent.channel_eventNo"
    val ChatChannelEvent_Channel = "ChatChannelEvent.channel"

    val ChatChannelMember_Channel_User = "ChatChannelMember_channel_user"
    val ChatChannelMember_Channel = "ChatChannelMember_channel"
  }

  object Sequences {
    val ChatChannelId = "chatChannelIdSeq"
  }
  
  object ChannelType extends Enumeration {
    val Group, Room, Direct = Value
  }

  def channelTypeString(channelType: ChannelType.Value): String = channelType match {
    case ChannelType.Group => "group"
    case ChannelType.Room => "room"
    case ChannelType.Direct => "direct"
  }
  
  object Fields {
    val Id = "id"
    val Type = "type"
    val Created = "created"
    val Private = "private"
    val Name = "name"
    val Topic = "topic"
    val Members = "members"

    val EventNo = "eventNo"
    val Channel = "channel"
    val User = "user"
    val Timestamp = "timestamp"

    val Message = "message"
    val UserAdded = "userAdded"
    val UserRemoved = "userRemoved"

    val Seen = "seen"

    val Username = "username"
  }

  def docToChatChannel(doc: ODocument): ChatChannel = {
    val created: Date = doc.field(Fields.Created, OType.DATETIME)

    ChatChannel(
      doc.field(Fields.Id),
      doc.field(Fields.Type),
      created.toInstant(),
      doc.field(Fields.Private),
      doc.field(Fields.Name),
      doc.field(Fields.Topic))
  }

  def chatChannelToDoc(chatChannel: ChatChannel): ODocument = {
    val doc = new ODocument(Classes.ChatChannel)
    doc.field(Fields.Id, chatChannel.id)
    doc.field(Fields.Type, chatChannel.channelType)
    doc.field(Fields.Created, Date.from(chatChannel.created))
    doc.field(Fields.Private, chatChannel.isPrivate)
    doc.field(Fields.Name, chatChannel.name)
    doc.field(Fields.Topic, chatChannel.topic)
    doc.field(Fields.Members, new HashSet[ORID]())
    doc
  }

  def docToChatChannelEvent(doc: ODocument): ChatChannelEvent = {
    val eventNo: Long = doc.field(Fields.EventNo)
    val channel: String = doc.field(Fields.Channel)
    val user: String = doc.field(Fields.User)
    val timestamp: Date = doc.field(Fields.Timestamp, OType.DATETIME)

    val className = doc.getClassName

    className match {
      case Classes.ChatMessageEvent =>
        val message: String = doc.field(Fields.Message)
        ChatMessageEvent(eventNo, channel, user, timestamp.toInstant(), message)
      case Classes.ChatUserJoinedEvent =>
        ChatUserJoinedEvent(eventNo, channel, user, timestamp.toInstant())
      case Classes.ChatUserLeftEvent =>
        ChatUserLeftEvent(eventNo, channel, user, timestamp.toInstant())
      case Classes.ChatUserAddedEvent =>
        val userAdded: String = doc.field(Fields.UserAdded)
        ChatUserAddedEvent(eventNo, channel, user, timestamp.toInstant(), userAdded)
      case Classes.ChatUserRemovedEvent =>
        val userRemoved: String = doc.field(Fields.UserRemoved)
        ChatUserRemovedEvent(eventNo, channel, user, timestamp.toInstant(), userRemoved)
      case Classes.ChatTopicChangedEvent =>
        val topic: String = doc.field(Fields.Topic)
        ChatTopicChangedEvent(eventNo, channel, user, timestamp.toInstant(), topic)
      case Classes.ChatNameChangedEvent =>
        val name: String = doc.field(Fields.Name)
        ChatNameChangedEvent(eventNo, channel, user, timestamp.toInstant(), name)
      case _ => ??? // TODO: Handle unknown event class 
    }
  }
}

class ChatChannelStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getChatChannel(channelId: String): Try[ChatChannel] = tryWithDb { db =>
    getChatChannelRid(channelId).map { rid =>
      docToChatChannel(rid.getRecord[ODocument])
    }.get
  }

  def createChatChannel(id: Option[String], channelType: ChannelType.Value, isPrivate: Boolean, name: String, topic: String): Try[String] = tryWithDb { db =>
    val channelId = id.getOrElse {
      "#" + db.getMetadata.getSequenceLibrary.getSequence(Sequences.ChatChannelId).next()
    }
    val doc = chatChannelToDoc(
        ChatChannel(channelId, channelTypeString(channelType), Instant.now(), isPrivate, name, topic))
    db.save(doc)
    channelId
  }

  def updateChatChannel(channelId: String, name: Option[String], topic: Option[String]): Try[Unit] = tryWithDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
    } yield {
      val doc = channelRid.getRecord[ODocument]
      name.foreach(doc.field(Fields.Name, _))
      topic.foreach(doc.field(Fields.Topic, _))
      doc.save()
      ()
    }
  }

  def removeChatChannel(channelId: String): Try[Unit] = tryWithDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
    } yield {
      channelRid.getRecord[ODocument].delete()
      ()
    }
  }

  def addChatMessageEvent(channelId: String, username: String, message: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatMessageEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  message = :message""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()),
        "message" -> message)
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatUserJoinedEvent(channelId: String, username: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatUserJoinedEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()))
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatUserLeftEvent(channelId: String, username: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatUserLeftEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()))
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatUserAddedEvent(channelId: String, username: String, userAdded: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatUserAddedEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  userAdded = :userAdded""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()),
        "userAdded" -> userAdded)
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatUserRemovedEvent(channelId: String, username: String, userRemoved: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatUserRemovedEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  userAdded = :userAdded""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()),
        "userRemoved" -> userRemoved)
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatNameChangedEvent(channelId: String, username: String, name: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatNameChangedEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  name = :name""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()),
        "name" -> name)
    db.command(query).execute(params.asJava)
    Unit
  }
  
  def addChatTopicChangedEvent(channelId: String, username: String, topic: String): Try[Unit] = tryWithDb { db =>
    val queryStirng =
      """INSERT INTO ChatTopicChangedEvent SET
        |  eventNo = (SELECT max(eventNo) + 1 FROM ChatChannelEvent WHERE channel.id = :channelId),
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  topic = :topic""".stripMargin
    val query = new OCommandSQL(queryStirng)
    val params = Map(
        "channelId" -> channelId, 
        "username" -> username, 
        "timestamp" -> Date.from(Instant.now()),
        "topic" -> topic)
    db.command(query).execute(params.asJava)
    Unit
  }

  def addChatChannelMember(channelId: String, username: String, seen: Option[Long]): Try[Unit] = tryWithDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
      userRid <- DomainUserStore.getUserRid(username, db)
    } yield {
      val doc = new ODocument(Classes.ChatChannelMember)
      doc.fields(Fields.Channel, channelRid)
      doc.fields(Fields.User, userRid)
      doc.fields(Fields.Seen, seen.getOrElse(0))
      db.save(doc)

      val channelDoc = channelRid.getRecord[ODocument]
      val members: JavaSet[ORID] = channelDoc.field(Fields.Members)
      members.add(doc.getIdentity)
      channelDoc.field(Fields.Members, members)
      channelDoc.save()
      ()
    }
  }

  def removeChatChannelMember(channelId: String, username: String): Try[Unit] = tryWithDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
      memberRid <- getChatChannelMemberRid(channelId, username)
    } yield {
      val channelDoc = channelRid.getRecord[ODocument]
      val members: JavaSet[ORID] = channelDoc.field(Fields.Members)
      members.remove(memberRid)
      channelDoc.field(Fields.Members, members)
      channelDoc.save()
      memberRid.getRecord[ODocument].delete()
      ()
    }
  }

  def markSeen(channelId: String, username: String, seen: Long): Try[Unit] = tryWithDb { db =>
    for {
      memberRid <- getChatChannelMemberRid(channelId, username)
    } yield {
      val doc = memberRid.getRecord[ODocument]
      doc.field(Fields.Seen, seen)
      doc.save()
      ()
    }
  }

  def getChatChannelEvents(channelId: String, offset: Option[Long], limit: Option[Long]): Try[List[ChatChannelEvent]] = tryWithDb { db =>
    val queryString = "SELECT FROM ChatChannelEvent WHERE channel.id = :channelId ORDER BY eventNo DESC"
    val limitString = limit.map(l => s"LIMIT $l").getOrElse("")
    val offsetString = offset.map(o => s"SKIP $o").getOrElse("")
    val params = Map("channelId" -> channelId)
    val result = QueryUtil.query(s"$queryString $limitString $offsetString", params, db)
    result.map { doc => docToChatChannelEvent(doc) }
  }

  def getChatChannelRid(channelId: String): Try[ORID] = tryWithDb { db =>
    QueryUtil.getRidFromIndex(Indexes.ChatChannel_Id, channelId, db).get
  }

  def getChatChannelMemberRid(channelId: String, username: String): Try[ORID] = tryWithDb { db =>
    val channelRID = getChatChannelRid(channelId).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, channelRID).asJava)
    QueryUtil.getRidFromIndex(Indexes.ChatChannelMember_Channel_User, key, db).get
  }
}
