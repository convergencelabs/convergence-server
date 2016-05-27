package com.convergencelabs.server.datastore

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.ActorLogging
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ActorRef
import com.convergencelabs.server.datastore.RegistrationActor.RegisterUser
import com.convergencelabs.server.datastore.RegistrationActor.AddRegistration
import com.convergencelabs.server.datastore.RegistrationActor.ApproveRegistration
import java.util.UUID
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import akka.actor.ActorRef
import scala.util.Try
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Success
import scala.util.Failure
import org.apache.commons.mail.HtmlEmail
import scala.concurrent.duration.FiniteDuration
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail
import com.typesafe.config.Config
import com.convergencelabs.server.datastore.RegistrationActor.RejectRegistration

object RegistrationActor {
  def props(dbPool: OPartitionedDatabasePool, userManager: ActorRef): Props = Props(new RegistrationActor(dbPool, userManager))

  case class RegisterUser(username: String, fname: String, lname: String, email: String, password: String, token: String)
  case class AddRegistration(fname: String, lname: String, email: String)
  case class ApproveRegistration(token: String)
  case class RejectRegistration(token: String)
}

class RegistrationActor private[datastore] (dbPool: OPartitionedDatabasePool, userManager: ActorRef) extends StoreActor with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(15 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val smtpConfig: Config = context.system.settings.config.getConfig("convergence.smtp")
  private[this] val username = smtpConfig.getString("username")
  private[this] val password = smtpConfig.getString("password")
  private[this] val toAddress = smtpConfig.getString("toAddress")
  private[this] val fromAddress = smtpConfig.getString("fromAddress")
  private[this] val host = smtpConfig.getString("host")
  private[this] val port = smtpConfig.getInt("port")

  private[this] val registrationStore = new RegistrationStore(dbPool)

  def receive: Receive = {
    case message: RegisterUser        => registerUser(message)
    case message: AddRegistration     => addRegistration(message)
    case message: ApproveRegistration => appoveRegistration(message)
    case message: RejectRegistration  => rejectRegistration(message)
    case message: Any                 => unhandled(message)
  }

  def registerUser(message: RegisterUser): Unit = {
    val origSender = sender
    val RegisterUser(username, fname, lname, email, password, token) = message
    registrationStore.isRegistrationApproved(email, token).map {
      case Some(true) => {
        val req = CreateConvergenceUserRequest(username, password, email, fname, lname)
        (userManager ? req).mapTo[CreateResult[String]] onSuccess {
          case result: CreateSuccess[String] => {
            registrationStore.removeRegistration(token)
            origSender ! result
          }
          case _ => origSender ! _
        }
      }
      case _ => origSender ! InvalidValue
    }
  }

  def addRegistration(message: AddRegistration): Unit = {
    val AddRegistration(fname, lname, email) = message
    reply(registrationStore.addRegistration(fname, lname, email) map {
      case CreateSuccess(token) => {
        val serverUrl = "http://localhost:8081"
        val templateHtml = html.registrationRequest(token, fname, lname, email, serverUrl)

        val approvalEmail = new HtmlEmail()
        approvalEmail.setHostName(host)
        approvalEmail.setSmtpPort(port)
        approvalEmail.setAuthenticator(new DefaultAuthenticator(username, password))
        approvalEmail.setFrom(fromAddress)
        approvalEmail.setSubject(s"Registration Request from ${fname} ${lname}")
        approvalEmail.setHtmlMsg(templateHtml.toString())
        approvalEmail.setTextMsg(s"Approval Link: ${serverUrl}/approval/${token}")
        approvalEmail.addTo(toAddress)
        approvalEmail.send()

        CreateSuccess(Unit)
      }
      case DuplicateValue => DuplicateValue
      case InvalidValue   => InvalidValue
    })

  }

  def appoveRegistration(message: ApproveRegistration): Unit = {
    val ApproveRegistration(token) = message

    val resp = registrationStore.approveRegistration(token)
    reply(resp)
    resp match {
      case Success(UpdateSuccess) => {
        registrationStore.getRegistrationEmail(token) match {
          case Success(Some(email)) => {
            val htmlBuilder = StringBuilder.newBuilder
            htmlBuilder ++= "<!DOCTYPE html>\n"
            htmlBuilder ++= "<html lang='en'>\n"
            htmlBuilder ++= "<head>\n"
            htmlBuilder ++= "  <meta charset='UTF-8'>\n"
            htmlBuilder ++= "  <title>Title</title>\n"
            htmlBuilder ++= "</head>\n"
            htmlBuilder ++= "<body>\n"
            htmlBuilder ++= s"  <a href='http://localhost:8081/signup/${token}'>Signup Page</a>\n"
            htmlBuilder ++= "</body>\n"
            htmlBuilder ++= "</html>\n"

            val approvalEmail = new HtmlEmail()
            approvalEmail.setHostName(host)
            approvalEmail.setSmtpPort(port)
            approvalEmail.setAuthenticator(new DefaultAuthenticator(username, password))
            approvalEmail.setFrom(fromAddress)
            approvalEmail.setSubject("Signup Approved")
            approvalEmail.setHtmlMsg(htmlBuilder.toString())
            approvalEmail.setTextMsg(s"Signup Link: http://localhost:8081/signup/${token}")
            approvalEmail.addTo(email)
            approvalEmail.send()
          }
          case _ => log.error("Unable to lookup registration to send Email")
        }
      }
      case _ => // Do Nothing, we have already replied with this error
    }
  }

  def rejectRegistration(message: RejectRegistration): Unit = {
    val RejectRegistration(token) = message
    reply(registrationStore.removeRegistration(token))
  }
}
