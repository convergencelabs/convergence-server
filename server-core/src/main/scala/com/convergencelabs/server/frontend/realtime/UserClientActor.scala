package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.domain.model.ClientModelDataRequest
import com.convergencelabs.server.domain.model.ClientModelDataResponse
import com.convergencelabs.server.domain.model.CloseRealtimeModelRequest
import com.convergencelabs.server.domain.model.CloseRealtimeModelSuccess
import com.convergencelabs.server.domain.model.CreateModelRequest
import com.convergencelabs.server.domain.model.CreateModelResponse
import com.convergencelabs.server.domain.model.DeleteModelRequest
import com.convergencelabs.server.domain.model.DeleteModelResponse
import com.convergencelabs.server.domain.model.ModelAlreadyExists
import com.convergencelabs.server.domain.model.ModelAlreadyOpen
import com.convergencelabs.server.domain.model.ModelCreated
import com.convergencelabs.server.domain.model.ModelDeleted
import com.convergencelabs.server.domain.model.ModelForceClose
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelNotFound
import com.convergencelabs.server.domain.model.OpenModelResponse
import com.convergencelabs.server.domain.model.OpenModelSuccess
import com.convergencelabs.server.domain.model.OpenRealtimeModelRequest
import com.convergencelabs.server.domain.model.OperationAcknowledgement
import com.convergencelabs.server.domain.model.OperationSubmission
import com.convergencelabs.server.domain.model.OutgoingOperation
import com.convergencelabs.server.domain.model.RealtimeModelClientMessage
import com.convergencelabs.server.domain.model.RemoteClientClosed
import com.convergencelabs.server.domain.model.RemoteClientOpened
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.model.ModelDeletedWhileOpening
import com.convergencelabs.server.domain.model.ClientDataRequestFailure
import com.convergencelabs.server.domain.model.NoSuchModel
import com.convergencelabs.server.domain.UserSearch
import com.convergencelabs.server.domain.UserList
import com.convergencelabs.server.domain.UserLookUpField
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.domain.UserList
import com.convergencelabs.server.domain.UserList
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.UserLookUp

object UserClientActor {
  def props(userServiceActor: ActorRef): Props =
    Props(new UserClientActor(userServiceActor))
}

class UserClientActor(userServiceActor: ActorRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingUserMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingUserMessage], replyPromise)
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: IncomingUserMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case userSearch: UserSearchMessage => onUserSearch(userSearch, replyCallback)
      case userLookUp: UserLookUpMessage => onUserLookUp(userLookUp, replyCallback)
    }
  }

  def onUserSearch(request: UserSearchMessage, cb: ReplyCallback): Unit = {
    val UserSearchMessage(fieldCodes, value, offset, limit, orderFieldCode, sortCode) = request

    val fields = fieldCodes.map { x => mapUserField(x) }
    val orderBy = orderFieldCode.map { x => mapUserField(x) }
    val sort = sortCode.map {
      case 0 => SortOrder.Ascending
      case _ => SortOrder.Descending
    }

    val future = this.userServiceActor ?
      UserSearch(fields, value, offset, limit, orderBy, sort)

    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map { x => mapDomainUser(x) }
        cb.reply(UserListMessage(userData))
      case Failure(cause) =>
        cb.unexpectedError("could not lookup users")
    }
  }

  def onUserLookUp(request: UserLookUpMessage, cb: ReplyCallback): Unit = {
    val UserLookUpMessage(fieldCode, values) = request
    val field = mapUserField(fieldCode)
    val future = this.userServiceActor ? UserLookUp(field, values)
    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map { x => mapDomainUser(x) }
        cb.reply(UserListMessage(userData))
      case Failure(cause) => cb.unexpectedError("could not lookup users")
    }
  }

  private[this] def mapUserField(fieldCode: Int): UserLookUpField.Value = {
    fieldCode match {
      case UserFieldCodes.Username => UserLookUpField.Username
      case UserFieldCodes.FirstName => UserLookUpField.FirstName
      case UserFieldCodes.LastName => UserLookUpField.LastName
      case UserFieldCodes.Email => UserLookUpField.Email
    }
  }

  private[this] def mapDomainUser(user: DomainUser): DomainUserData = {
    val DomainUser(username, firstname, lastName, displayName, email) = user
    DomainUserData(username, firstname, lastName, displayName, email)
  }

  private[this] object UserFieldCodes extends Enumeration {
    val UserId = 0;
    val Username = 1;
    val FirstName = 2;
    val LastName = 3;
    val Email = 4;
  }
}
