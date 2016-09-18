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
import com.convergencelabs.server.datastore.ConvergenceUserManagerActor.CreateConvergenceUserRequest
import akka.actor.ActorRef
import scala.util.Try
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.Config
import com.convergencelabs.server.datastore.RegistrationActor.RejectRegistration
import java.net.URLEncoder
import com.convergencelabs.templates
import com.convergencelabs.server.util.EmailUtilities

object RegistrationActor {
  def props(dbPool: OPartitionedDatabasePool, userManager: ActorRef): Props = Props(new RegistrationActor(dbPool, userManager))

  case class RegisterUser(username: String, fname: String, lname: String, email: String, password: String, token: String)
  case class AddRegistration(fname: String, lname: String, email: String, reason: String)
  case class ApproveRegistration(token: String)
  case class RejectRegistration(token: String)
}

class RegistrationActor private[datastore] (dbPool: OPartitionedDatabasePool, userManager: ActorRef) extends StoreActor with ActorLogging {

  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(15 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val smtpConfig: Config = context.system.settings.config.getConfig("convergence.smtp")
  
  private[this] val restPublicEndpoint = context.system.settings.config.getString("convergence.rest-public-endpoint")
  private[this] val adminUiServerUrl = context.system.settings.config.getString("convergence.admin-ui-uri")

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
        val req = CreateConvergenceUserRequest(username, email, fname, lname, password)
        (userManager ? req).mapTo[CreateResult[String]] onSuccess {
          case result: CreateSuccess[String] => {
            registrationStore.removeRegistration(token)
            
            val welcomeTxt = if(fname != null && fname.nonEmpty) s"${fname}, welcome" else "Welcome"
            val templateHtml = templates.email.html.accountCreated(username, welcomeTxt)
            val templateTxt = templates.email.txt.accountCreated(username, welcomeTxt)
            val newAccountEmail = EmailUtilities.createHtmlEmail(smtpConfig, templateHtml, templateTxt.toString())
            newAccountEmail.setSubject(s"${welcomeTxt} to Convergence!")
            newAccountEmail.addTo(email)
            newAccountEmail.send()
            
            origSender ! result
          }
          case _ => origSender ! _
        }
      }
      case _ => origSender ! InvalidValue
    }
  }

  def addRegistration(message: AddRegistration): Unit = {
    val AddRegistration(fname, lname, email, reason) = message
    reply(registrationStore.addRegistration(fname, lname, email, reason) map {
      case CreateSuccess(token) => {
        val bodyContent = templates.email.internal.txt.registrationRequest(token, fname, lname, email, reason, restPublicEndpoint)
        val internalEmail = EmailUtilities.createTextEmail(smtpConfig, bodyContent.toString())
        internalEmail.setSubject(s"Registration Request from ${fname} ${lname}")
        internalEmail.addTo(smtpConfig.getString("new-registration-to-address"))
        internalEmail.send()
        
        val template = templates.email.html.accountRequested(fname)
        val templateTxt = templates.email.txt.accountRequested(fname)
        val newRequestEmail = EmailUtilities.createHtmlEmail(smtpConfig, template, templateTxt.toString())
        newRequestEmail.setSubject(s"${fname}, your account request has been received")
        newRequestEmail.addTo(email)
        newRequestEmail.send()

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
        registrationStore.getRegistrationInfo(token) match {
          case Success(Some(registration)) => {
            val (firstName, lastName, email) = registration
            
            val fnameEncoded = URLEncoder.encode(firstName, "UTF8")
            val lnameEncoded = URLEncoder.encode(lastName, "UTF8")
            val emailEncoded = URLEncoder.encode(email, "UTF8")
            val signupUrl = s"${adminUiServerUrl}/signup/${token}?fname=${fnameEncoded}&lname=${lnameEncoded}&email=${emailEncoded}"
            val introTxt = if(firstName != null && firstName.nonEmpty) s"${firstName}, good news!" else "Good news!"
            
            val templateHtml = templates.email.html.registrationApproved(signupUrl, introTxt)
            val templateTxt = templates.email.txt.registrationApproved(signupUrl, introTxt)

            val approvalEmail = EmailUtilities.createHtmlEmail(smtpConfig, templateHtml, templateTxt.toString());
            approvalEmail.setSubject(s"Your Convergence account request has been approved")
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
