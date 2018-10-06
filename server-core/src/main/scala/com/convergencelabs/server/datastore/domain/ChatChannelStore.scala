package com.convergencelabs.server.datastore.domain

import java.time.Instant
import java.util.Date
import java.util.HashSet
import java.util.{ Set => JavaSet }

import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.JavaConverters.setAsJavaSetConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.AbstractDatabasePersistence
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.ChatChannelStore.ChannelType
import com.convergencelabs.server.datastore.domain.ChatChannelStore.Fields
import com.convergencelabs.server.datastore.domain.ChatChannelStore.channelTypeString
import com.convergencelabs.server.datastore.domain.ChatChannelStore.chatChannelToDoc
import com.convergencelabs.server.datastore.domain.ChatChannelStore.docToChatChannel
import com.convergencelabs.server.datastore.domain.ChatChannelStore.docToChatChannelEvent
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.index.OCompositeKey
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.MultipleValuesException
import com.orientechnologies.orient.core.db.record.OIdentifiable

case class ChatChannel(
  id:          String,
  channelType: String,
  created:     Instant,
  isPrivate:   Boolean,
  name:        String,
  topic:       String)

case class ChatChannelInfo(
  id:            String,
  channelType:   String,
  created:       Instant,
  isPrivate:     Boolean,
  name:          String,
  topic:         String,
  members:       Set[String],
  lastEventNo:   Long,
  lastEventTime: Instant)

sealed trait ChatChannelEvent {
  val eventNo: Long
  val channel: String
  val user: String
  val timestamp: Instant
}

case class ChatCreatedEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant,
  name:      String,
  topic:     String,
  members:   Set[String]) extends ChatChannelEvent

case class ChatMessageEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant,
  message:   String) extends ChatChannelEvent

case class ChatUserJoinedEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant) extends ChatChannelEvent

case class ChatUserLeftEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant) extends ChatChannelEvent

case class ChatUserAddedEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant,
  userAdded: String) extends ChatChannelEvent

case class ChatUserRemovedEvent(
  eventNo:     Long,
  channel:     String,
  user:        String,
  timestamp:   Instant,
  userRemoved: String) extends ChatChannelEvent

case class ChatNameChangedEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant,
  name:      String) extends ChatChannelEvent

case class ChatTopicChangedEvent(
  eventNo:   Long,
  channel:   String,
  user:      String,
  timestamp: Instant,
  topic:     String) extends ChatChannelEvent

case class ChatChannelMember(channel: String, user: String, seen: Long)

object ChatChannelStore {

  object ChannelType extends Enumeration {
    val Group, Room, Direct = Value

    def withNameOpt(s: String): Option[Value] = values.find(_.toString.toLowerCase() == s.toLowerCase())
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
    val created: Date = doc.getProperty(Fields.Created)

    ChatChannel(
      doc.getProperty(Fields.Id),
      doc.getProperty(Fields.Type),
      created.toInstant(),
      doc.getProperty(Fields.Private),
      doc.getProperty(Fields.Name),
      doc.getProperty(Fields.Topic))
  }

  def chatChannelToDoc(chatChannel: ChatChannel): ODocument = {
    val doc = new ODocument(Schema.Classes.ChatChannel.Class)
    doc.setProperty(Fields.Id, chatChannel.id)
    doc.setProperty(Fields.Type, chatChannel.channelType)
    doc.setProperty(Fields.Created, Date.from(chatChannel.created))
    doc.setProperty(Fields.Private, chatChannel.isPrivate)
    doc.setProperty(Fields.Name, chatChannel.name)
    doc.setProperty(Fields.Topic, chatChannel.topic)
    doc.setProperty(Fields.Members, new HashSet[ORID]())
    doc
  }

  def docToChatChannelEvent(doc: ODocument): ChatChannelEvent = {
    val eventNo: Long = doc.getProperty(Fields.EventNo)
    val channel: String = doc.getProperty("channel.id")
    val user: String = doc.getProperty("user.username")
    val timestamp: Date = doc.getProperty(Fields.Timestamp)

    val className = doc.getClassName

    className match {
      case Schema.Classes.ChatCreatedEvent.Class =>
        val name: String = doc.getProperty(Fields.Name)
        val topic: String = doc.getProperty(Fields.Topic)
        val members: JavaSet[ODocument] = doc.getProperty("members")
        val usernames: Set[String] = members.asScala.toSet.map { doc: ODocument => doc.getProperty("username").asInstanceOf[String] }
        ChatCreatedEvent(eventNo, channel, user, timestamp.toInstant(), name, topic, usernames)
      case Schema.Classes.ChatMessageEvent.Class =>
        val message: String = doc.getProperty(Fields.Message)
        ChatMessageEvent(eventNo, channel, user, timestamp.toInstant(), message)
      case Schema.Classes.ChatUserJoinedEvent.Class =>
        ChatUserJoinedEvent(eventNo, channel, user, timestamp.toInstant())
      case Schema.Classes.ChatUserLeftEvent.Class =>
        ChatUserLeftEvent(eventNo, channel, user, timestamp.toInstant())
      case Schema.Classes.ChatUserAddedEvent.Class =>
        val userAdded: String = doc.getProperty("userAdded.username")
        ChatUserAddedEvent(eventNo, channel, user, timestamp.toInstant(), userAdded)
      case Schema.Classes.ChatUserRemovedEvent.Class =>
        val userRemoved: String = doc.getProperty("userRemoved.username")
        ChatUserRemovedEvent(eventNo, channel, user, timestamp.toInstant(), userRemoved)
      case Schema.Classes.ChatTopicChangedEvent.Class =>
        val topic: String = doc.getProperty(Fields.Topic)
        ChatTopicChangedEvent(eventNo, channel, user, timestamp.toInstant(), topic)
      case Schema.Classes.ChatNameChangedEvent.Class =>
        val name: String = doc.getProperty(Fields.Name)
        ChatNameChangedEvent(eventNo, channel, user, timestamp.toInstant(), name)
      case _ =>
        throw new IllegalArgumentException(s"Unknown Chat Channel Event class name: ${className}")
    }
  }
}

class ChatChannelStore(private[this] val dbProvider: DatabaseProvider) extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getChatChannelInfo(channelId: String): Try[ChatChannelInfo] = {
    getChatChannelInfo(List(channelId)).flatMap {
      _ match {
        case first :: Nil => Success(first)
        case Nil => Failure(EntityNotFoundException())
        case _ => Failure(MultipleValuesException())
      }
    }
  }

  // FIXME is this the best way to do this?
  private[this] val ChatChannelInfoQuery =
    """
        |SELECT 
        |  max(eventNo) as eventNo, max(timestamp) as timestamp,
        |  channel.id as id, channel.type as type, channel.created as created,
        |  channel.private as private, channel.name as name, channel.topic as topic,
        |  channel.members as members
        |FROM
        |  ChatChannelEvent 
        |WHERE
        |  channel.id IN :channelIds
        |GROUP BY (channel)""".stripMargin
  def getChatChannelInfo(channelId: List[String]): Try[List[ChatChannelInfo]] = withDb { db =>
    val params = Map("channelIds" -> channelId.asJava)
    OrientDBUtil
      .queryAndMap(db, ChatChannelInfoQuery, params) { doc =>
        val id: String = doc.getProperty("id")
        val channelType: String = doc.getProperty("type")
        val created: Date = doc.getProperty("created")
        val isPrivate: Boolean = doc.getProperty("private")
        val name: String = doc.getProperty("name")
        val topic: String = doc.getProperty("topic")
        val members: JavaSet[OIdentifiable] = doc.getProperty("members")
        val usernames: Set[String] = members.asScala.map(member =>
          member.getRecord.asInstanceOf[ODocument].field("user.username").asInstanceOf[String]).toSet
        val lastEventNo: Long = doc.getProperty("eventNo")
        val lastEventTime: Date = doc.getProperty("timestamp")
        ChatChannelInfo(id, channelType, created.toInstant(), isPrivate, name, topic,
          usernames, lastEventNo, lastEventTime.toInstant())
      }
  }

  def getChatChannel(channelId: String): Try[ChatChannel] = {
    getChatChannelRid(channelId)
      .flatMap(rid => Try(rid.getRecord[ODocument]))
      .map(docToChatChannel(_))
  }

  def createChatChannel(
    id:           Option[String],
    channelType:  ChannelType.Value,
    creationTime: Instant,
    isPrivate:    Boolean,
    name:         String,
    topic:        String,
    members:      Option[Set[String]],
    createdBy:    String): Try[String] = tryWithDb { db =>
    // FIXME: return failure if addAllChatChannelMembers fails
    db.begin()
    val channelId = id.getOrElse {
      "#" + db.getMetadata.getSequenceLibrary.getSequence(Schema.Sequences.ChatChannelId).next()
    }
    val doc = chatChannelToDoc(ChatChannel(channelId, channelTypeString(channelType), creationTime, isPrivate, name, topic))
    db.save(doc)
    db.commit()

    members.foreach { username =>
      addAllChatChannelMembers(channelId, username, None).get
    }

    // FIXME why is this needed? It seems like the above might put another db into the active thread.
    db.activateOnCurrentThread()

    db.commit()
    this.addChatCreatedEvent(ChatCreatedEvent(0, channelId, createdBy, creationTime, name, topic, members.getOrElse(Set()))).get

    db.activateOnCurrentThread()
    db.commit()
    channelId
  } recoverWith {
    case e: ORecordDuplicatedException =>
      e.getIndexName match {
        case Schema.Classes.ChatChannel.Indices.Id =>
          Failure(DuplicateValueException("id"))
        case _ =>
          Failure(e)
      }
  }

  // TODO is there a better way to do this using ChatChannelMember class, like maybe with
  // a group by / count WHERE'd on the Channel Link?
  private[this] val DirectChatChannelInfoByUsersQuery =
    """
       |SELECT 
       |  id 
       |FROM ChatChannel 
       |WHERE 
       |  members CONTAINSALL (user.username IN :usernames) AND
       |  members.size() = :size AND 
       |  type='direct'""".stripMargin
  def getDirectChatChannelInfoByUsers(users: Set[String]): Try[Option[ChatChannelInfo]] = withDb { db =>
    // TODO is there a way to do this in one step not two?
    val params = Map("usernames" -> users.asJava, "size" -> users.size)
    OrientDBUtil
      .findDocument(db, DirectChatChannelInfoByUsersQuery, params)
      .flatMap {
        _ match {
          case Some(doc) =>
            val id: String = doc.getProperty("id")
            this.getChatChannelInfo(id).map(Some(_))
          case None =>
            Success(None)
        }
      }
  }

  private[this] val GetJoinedChannelsQeury =
    """
       |SELECT 
       |  channel.id as channelId
       |FROM 
       |  ChatChannelMember
       |WHERE 
       |  user.username = :username AND 
       |  channel.type='group'""".stripMargin
  def getJoinedChannels(username: String): Try[List[ChatChannelInfo]] = withDb { db =>
    val params = Map("username" -> username)
    OrientDBUtil
      .query(db, GetJoinedChannelsQeury, params)
      .flatMap(docs => getChatChannelInfo(docs.map(_.getProperty("channelId").asInstanceOf[String])))
  }

  def updateChatChannel(channelId: String, name: Option[String], topic: Option[String]): Try[Unit] = {
    getChatChannelRid(channelId).flatMap { channelRid =>
      Try {
        val doc = channelRid.getRecord[ODocument]
        name.foreach(doc.field(Fields.Name, _))
        topic.foreach(doc.field(Fields.Topic, _))
        doc.save()
        ()
      }
    }
  }

  def removeChatChannel(channelId: String): Try[Unit] = {
    getChatChannelRid(channelId).flatMap { channelRid =>
      Try {
        channelRid.getRecord[ODocument].delete()
        ()
      }
    }
  }

  // TODO: All of the events are very similar, need to abstract some of each of these methods

  def addChatCreatedEvent(event: ChatCreatedEvent): Try[Unit] = withDb { db =>
    val ChatCreatedEvent(eventNo, channel, user, timestamp, name, topic, members) = event

    val memberQueryString = "SELECT FROM User WHERE username IN :members"
    val memberParams = Map("members" -> members.asJava)
    OrientDBUtil
      .query(db, memberQueryString, memberParams)
      .map(_.toSet)
      .flatMap { users =>
        val query =
          """INSERT INTO ChatCreatedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  name = :name,
        |  topic = :topic,
        |  members = :members""".stripMargin
        val params = Map(
          "eventNo" -> eventNo,
          "channelId" -> channel,
          "username" -> user,
          "timestamp" -> Date.from(timestamp),
          "name" -> name,
          "topic" -> topic,
          "members" -> users.asJava)

        // FIXME we need a way to make sure that the event actually got saved. The result should be the
        // created ODocument we need to make sure.
        OrientDBUtil
          .command(db, query, params)
          .map(_ => ())
      }
  }

  def addChatMessageEvent(event: ChatMessageEvent): Try[Unit] = withDb { db =>
    val ChatMessageEvent(eventNo, channel, user, timestamp, message) = event
    val query =
      """INSERT INTO ChatMessageEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  message = :message""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp),
      "message" -> message)
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatUserJoinedEvent(event: ChatUserJoinedEvent): Try[Unit] = withDb { db =>
    val ChatUserJoinedEvent(eventNo, channel, user, timestamp) = event
    val query =
      """INSERT INTO ChatUserJoinedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp))
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatUserLeftEvent(event: ChatUserLeftEvent): Try[Unit] = withDb { db =>
    val ChatUserLeftEvent(eventNo, channel, user, timestamp) = event
    val query =
      """INSERT INTO ChatUserLeftEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp))
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatUserAddedEvent(event: ChatUserAddedEvent): Try[Unit] = withDb { db =>
    val ChatUserAddedEvent(eventNo, channel, user, timestamp, userAdded) = event
    val query =
      """INSERT INTO ChatUserAddedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  userAdded = (SELECT FROM User WHERE username = :userAdded)""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp),
      "userAdded" -> userAdded)
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatUserRemovedEvent(event: ChatUserRemovedEvent): Try[Unit] = withDb { db =>
    val ChatUserRemovedEvent(eventNo, channel, user, timestamp, userRemoved) = event
    val query =
      """INSERT INTO ChatUserRemovedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  userRemoved = (SELECT FROM User WHERE username = :userRemoved)""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp),
      "userRemoved" -> userRemoved)
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatNameChangedEvent(event: ChatNameChangedEvent): Try[Unit] = withDb { db =>
    val ChatNameChangedEvent(eventNo, channel, user, timestamp, name) = event
    val query =
      """INSERT INTO ChatNameChangedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  name = :name""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp),
      "name" -> name)
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def addChatTopicChangedEvent(event: ChatTopicChangedEvent): Try[Unit] = withDb { db =>
    val ChatTopicChangedEvent(eventNo, channel, user, timestamp, topic) = event
    val query =
      """INSERT INTO ChatTopicChangedEvent SET
        |  eventNo = :eventNo,
        |  channel = (SELECT FROM ChatChannel WHERE id = :channelId),
        |  user = (SELECT FROM User WHERE username = :username),
        |  timestamp = :timestamp,
        |  topic = :topic""".stripMargin
    val params = Map(
      "eventNo" -> eventNo,
      "channelId" -> channel,
      "username" -> user,
      "timestamp" -> Date.from(timestamp),
      "topic" -> topic)
    OrientDBUtil
      .command(db, query, params)
      .map(_ => ())
  }

  def getChatChannelMembers(channelId: String): Try[Set[String]] = withDb { db =>
    val query = "SELECT user.username as member FROM ChatChannelMember WHERE channel.id = :channelId"
    val params = Map("channelId" -> channelId)

    OrientDBUtil
      .queryAndMap(db, query, params)(_.getProperty("member").asInstanceOf[String])
      .map(_.toSet)
  }

  def addAllChatChannelMembers(channelId: String, usernames: Set[String], seen: Option[Long]): Try[Unit] = withDb { db =>
    val seenVal = seen.getOrElse(0)
    // FIXME we should do all of this in a transaction so they all succeed or fail
    getChatChannelRid(channelId)
      .flatMap { channelRid =>
        val results = usernames.map { username =>
          DomainUserStore.getUserRid(username, db)
            .flatMap(userRid => addChatChannelMemeber(db, channelRid, userRid, seen))
        }

        Try(results.map(_.get)).map(_ => ())
      }
  }

  def addChatChannelMember(channelId: String, username: String, seen: Option[Long]): Try[Unit] = withDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
      userRid <- DomainUserStore.getUserRid(username, db)
    } yield {
      addChatChannelMemeber(db, channelRid, userRid, seen)
    }
  }

  private[this] def addChatChannelMemeber(db: ODatabaseDocument, channelRid: ORID, userRid: ORID, seen: Option[Long]): Try[Unit] = Try {
    val doc = db.newElement(Schema.Classes.ChatChannelMember.Class)
    doc.setProperty(Fields.Channel, channelRid)
    doc.setProperty(Fields.User, userRid)
    doc.setProperty(Fields.Seen, seen.getOrElse(0))
    db.save(doc)

    val channelDoc = channelRid.getRecord[ODocument]
    val members: JavaSet[ORID] = channelDoc.field(Fields.Members)
    members.add(doc.getIdentity)
    channelDoc.field(Fields.Members, members)
    channelDoc.save()
  }

  def removeChatChannelMember(channelId: String, username: String): Try[Unit] = withDb { db =>
    for {
      channelRid <- getChatChannelRid(channelId)
      memberRid <- getChatChannelMemberRid(channelId, username)
    } yield {
      Try {
        val channelDoc = channelRid.getRecord[ODocument]
        val members: JavaSet[ORID] = channelDoc.field(Fields.Members)
        members.remove(memberRid)
        channelDoc.field(Fields.Members, members)
        channelDoc.save()
        memberRid.getRecord[ODocument].delete()
        ()
      }
    }
  }

  def markSeen(channelId: String, username: String, seen: Long): Try[Unit] = tryWithDb { db =>
    getChatChannelMemberRid(channelId, username).flatMap { memberRid =>
      Try {
        val doc = memberRid.getRecord[ODocument]
        doc.field(Fields.Seen, seen)
        doc.save()
        ()
      }
    }
  }

  def getChatChannelEvents(channelId: String, eventTypes: Option[List[String]], startEvent: Option[Int], limit: Option[Int], forward: Option[Boolean]): Try[List[ChatChannelEvent]] = withDb { db =>
    val params = scala.collection.mutable.Map[String, Any]("channelId" -> channelId)

    val eventTypesClause = eventTypes.getOrElse(List()) match {
      case Nil =>
        ""
      case types =>
        params("types") = types.map(getClassName(_)).filter(_.isDefined).map(_.get).asJava
        "AND @class IN :types"
    }

    val fwd = forward.getOrElse(false)
    val eventNoClaue = startEvent map { eventNo =>
      val operator = fwd match {
        case true => ">="
        case false => "<="
      }
      params("startEventNo") = eventNo
      s" AND eventNo ${operator} :startEventNo"
    } getOrElse ("")

    val orderBy = fwd match {
      case true => "ASC"
      case false => "DESC"
    }

    val baseQuery = s"SELECT FROM ChatChannelEvent WHERE channel.id = :channelId ${eventTypesClause} ${eventNoClaue} ORDER BY eventNo ${orderBy}"
    val query = OrientDBUtil.buildPagedQuery(baseQuery, Some(limit.getOrElse(50)), Some(0))

    OrientDBUtil
      .query(db, query, params.toMap)
      .map(_.map(docToChatChannelEvent(_)).sortWith((e1, e2) => e1.eventNo < e2.eventNo))
  }

  def getChatChannelRid(channelId: String): Try[ORID] = withDb { db =>
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Classes.ChatChannel.Indices.Id, channelId)
  }

  def getChatChannelMemberRid(channelId: String, username: String): Try[ORID] = withDb { db =>
    val channelRID = getChatChannelRid(channelId).get
    val userRID = DomainUserStore.getUserRid(username, db).get
    val key = new OCompositeKey(List(userRID, channelRID).asJava)
    OrientDBUtil.getIdentityFromSingleValueIndex(db, Schema.Classes.ChatChannelMember.Indices.Channel_User, key)
  }

  def getClassName: PartialFunction[String, Option[String]] = {
    case "message" => Some(Schema.Classes.ChatMessageEvent.Class)
    case "created" => Some(Schema.Classes.ChatCreatedEvent.Class)
    case "user_joined" => Some(Schema.Classes.ChatUserJoinedEvent.Class)
    case "user_left" => Some(Schema.Classes.ChatUserLeftEvent.Class)
    case "user_added" => Some(Schema.Classes.ChatUserAddedEvent.Class)
    case "user_removed" => Some(Schema.Classes.ChatUserRemovedEvent.Class)
    case "name_changed" => Some(Schema.Classes.ChatNameChangedEvent.Class)
    case "topic_changed" => Some(Schema.Classes.ChatTopicChangedEvent.Class)
    case _ => None
  }
}
