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

object RegistrationActor {
  def props(dbPool: OPartitionedDatabasePool, userManager: ActorRef): Props = Props(new RegistrationActor(dbPool, userManager))

  case class RegisterUser(username: String, fname: String, lname: String, email: String, password: String, token: String)
  case class AddRegistration(fname: String, lname: String, email: String)
  case class ApproveRegistration(email: String, token: String)

}

class RegistrationActor private[datastore] (dbPool: OPartitionedDatabasePool, userManager: ActorRef) extends StoreActor with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(15 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val registrationStore = new RegistrationStore(dbPool)

  def receive: Receive = {
    case message: RegisterUser        => registerUser(message)
    case message: AddRegistration     => addRegistration(message)
    case message: ApproveRegistration => appoveRegistration(message)
    case message: Any                 => unhandled(message)
  }

  def registerUser(message: RegisterUser): Unit = {
    val origSender = sender
    val RegisterUser(username, fname, lname, email, password, token) = message
    registrationStore.isRegistrationApproved(email, token).map {
      case Some(true) => {
        val req = CreateConvergenceUserRequest(username, password)
        (userManager ? req).mapTo[CreateResult[String]] onSuccess {
          case result: CreateSuccess[String] => {
            registrationStore.removeRegistration(email, token)
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

        val smtpEmail = ""
        val smtpPassword = ""
        val sendFrom = "cameron@convergencelabs.com"
        val sendTo = "cameron@convergencelabs.com"

        val htmlBuilder = StringBuilder.newBuilder
        htmlBuilder ++= "<!DOCTYPE html>\n"
        htmlBuilder ++= "<html lang='en'>\n"
        htmlBuilder ++= "<head>\n"
        htmlBuilder ++= "	<meta charset='UTF-8'>\n"
        htmlBuilder ++= "	<title>Title</title>\n"
        htmlBuilder ++= "</head>\n"
        htmlBuilder ++= "<body>\n"
        htmlBuilder ++= "	<button id='approveButton' onclick='post();'>Approve</button>\n"
        htmlBuilder ++= "</body>\n"

        htmlBuilder ++= "<script>\n"
        htmlBuilder ++= "	function post(callback) {\n"
        htmlBuilder ++= "		try {\n"
        htmlBuilder ++= "			var req = new XMLHttpRequest();\n"
        htmlBuilder ++= "     req.open('POST', 'http://localhost:8081/rest/registration/approve', true);\n"
        htmlBuilder ++= "     req.setRequestHeader('Content-type', 'application/json');\n"
        htmlBuilder ++= "     req.setRequestHeader('Access-Control-Allow-Origin', '*');\n"
        htmlBuilder ++= s"     req.send(JSON.stringify({'email': '${email}', 'token': '${token}'}));\n"
        htmlBuilder ++= "    } catch (e) {\n"
        htmlBuilder ++= "      window.console && console.log(e);\n"
        htmlBuilder ++= "    }\n"
        htmlBuilder ++= "  }\n"
        htmlBuilder ++= "</script>\n"
        htmlBuilder ++= "</html>\n"

        val approvalEmail = new HtmlEmail()
        approvalEmail.setHostName("smtp.gmail.com")
        approvalEmail.setSmtpPort(465)
        approvalEmail.setAuthenticator(new DefaultAuthenticator(smtpEmail, smtpPassword))
        approvalEmail.setSSLOnConnect(true)
        approvalEmail.setFrom(sendFrom)
        approvalEmail.setSubject(s"Registration Approval Request for ${fname} ${lname}")
        approvalEmail.setHtmlMsg(htmlBuilder.toString())
        // Todo: Need to set alternative msg
        approvalEmail.setTextMsg("")
        approvalEmail.addTo(sendTo)
        approvalEmail.send()

        CreateSuccess(Unit)
      }
      case DuplicateValue => DuplicateValue
      case InvalidValue   => InvalidValue
    })

  }

  def appoveRegistration(message: ApproveRegistration): Unit = {
    val ApproveRegistration(email, token) = message
    reply(registrationStore.approveRegistration(email, token))
    // Send Registation Email To User
  }
}
