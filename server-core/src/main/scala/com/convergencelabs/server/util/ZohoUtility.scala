package com.convergencelabs.server.util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.stream.ActorMaterializer
import akka.util.ByteString


object ZohoUtility {
  sealed trait ZohoData {
    def toXml(): String
  }

  case class Lead(
      firstName: String,
      lastName: String,
      email: String,
      company: Option[String],
      title: Option[String],
      notes: String) extends ZohoData {

    def toXml(): String = {
      val xmlLead =
        <Leads>
          <row no="1">
            <FL val="First Name">{ firstName }</FL>
            <FL val="Last Name">{ lastName }</FL>
            <FL val="Email">{ email }</FL>
            <FL val="Company">{ company.getOrElse("") }</FL>
            <FL val="Title">{ title.getOrElse("") }</FL>
            <FL val="Lead Source">Request Invite Form</FL>
            <FL val="Lead Status">Not Contacted</FL>
            <FL val="Invite Request Detail">{ notes }</FL>
            <FL val="Approved">false</FL>
          </row>
        </Leads>
      scala.xml.Utility.trim(xmlLead).toString
    }
  }
}

class ZohoUtility(private val authtoken: String,
    private implicit val system: ActorSystem,
    private implicit val ec: ExecutionContext) {

  import ZohoCrmClient._
  import ZohoUtility._

  private[this] val client = new ZohoCrmClient(authtoken, system, ec)

  def createLead(lead: Lead): Future[Unit] = {
    client.insertRecords(RecordType.Leads, lead.toXml).map(_ => ())
  }

  def approveLead(email: String): Future[Unit] = {
    getLeadIdByEmail(email) flatMap { id =>
      val xmlData =
        <Leads>
          <row no="1">
            <FL val="Approved">true</FL>
          </row>
        </Leads>
      client.updateRecords(RecordType.Leads, id, scala.xml.Utility.trim(xmlData).toString)
    } map { _ =>
      ()
    }
  }
  
  def convertLead(email: String): Future[Unit] = {
    getLeadIdByEmail(email) flatMap { leadId =>
      client.convertLead(leadId)
    } map { _ =>
      ()
    }
  }

  private[this] def getLeadIdByEmail(email: String): Future[String] = {
    client.findRecordByEmail(ZohoCrmClient.RecordType.Leads, email).map { f =>
      val xml = scala.xml.XML.loadString(f)
      val id = (xml \\ "result" \\ "Leads" \\ "row" \\ "FL")
        .filter(_.attribute("val").filter(_.text == "LEADID").isDefined).text
      id
    }
  }
}

object ZohoCrmClient {
  object ApiMethod extends Enumeration {
    val InsertRecords = Value("insertRecords")
    val UpdateRecords = Value("updateRecords")
    val SearchRecords = Value("searchRecords")
    val ConvertLead = Value("convertLead")
  }

  object RecordType extends Enumeration {
    val Contacts = Value("Contacts")
    val Leads = Value("Leads")
  }
}

class ZohoCrmClient(
    private val authtoken: String,
    private implicit val system: ActorSystem,
    private implicit val ec: ExecutionContext) {

  import ZohoCrmClient._

  private[this] implicit val materializer = ActorMaterializer()

  def insertRecords(recordType: ZohoCrmClient.RecordType.Value, xmlData: String): Future[String] = {
    val query = Query(
      ("newFormat" -> "1"),
      ("authtoken" -> authtoken),
      ("scope" -> "crmapi"),
      ("xmlData" -> xmlData))
    val uri = Uri(constructUrl(ApiMethod.InsertRecords, recordType)).withQuery(query)
    post(uri)
  }

  def updateRecords(recordType: RecordType.Value, id: String, xmlData: String): Future[String] = {
    val query = Query(
      ("newFormat" -> "1"),
      ("authtoken" -> authtoken),
      ("scope" -> "crmapi"),
      ("id" -> id),
      ("xmlData" -> xmlData))
    val uri = Uri(constructUrl(ApiMethod.UpdateRecords, recordType)).withQuery(query)
    post(uri)
  }

  def convertLead(leadId: String): Future[String] = {
    val xml =
      <Potentials>
        <row no="1">
          <option val="createPotential">false</option>
          <option val="assignTo">alec@convergencelabs.com</option>
          <option val="notifyLeadOwner">true</option>
          <option val="notifyNewEntityOwner">true</option>
        </row>
      </Potentials>

    val query = Query(
      ("newFormat" -> "1"),
      ("authtoken" -> authtoken),
      ("scope" -> "crmapi"),
      ("leadId" -> leadId),
      ("xmlData" -> scala.xml.Utility.trim(xml).toString))
    val uri = Uri(constructUrl(ApiMethod.ConvertLead, RecordType.Leads)).withQuery(query)
    post(uri)
  }

  def findRecordByEmail(recordType: ZohoCrmClient.RecordType.Value, email: String): Future[String] = {
    val query = Query(
      ("authtoken" -> authtoken),
      ("scope" -> "crmapi"),
      ("criteria" -> s"(Email:${email})"))
    val uri = Uri(constructUrl(ZohoCrmClient.ApiMethod.SearchRecords, recordType)).withQuery(query)
    get(uri)
  }

  private[this] def post(uri: Uri): Future[String] = {
    execute(HttpMethods.POST, uri)
  }

  private[this] def get(uri: Uri): Future[String] = {
    execute(HttpMethods.GET, uri)
  }

  private[this] def execute(method: HttpMethod, uri: Uri): Future[String] = {
    val request = HttpRequest(method = method, uri = uri)
    val f: Future[HttpResponse] = Http(system).singleRequest(request)

    f flatMap {
      case resp @ HttpResponse(StatusCodes.OK, headers, entity, _) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map { body =>
          body.utf8String
        }
      case HttpResponse(code, headers, entity, _) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).flatMap { body =>
          Future.failed(new RuntimeException(s"Unsuccessful http response '${code}': ${body.utf8String}"))
        }
    }
  }

  private[this] def constructUrl(method: ZohoCrmClient.ApiMethod.Value, recordType: ZohoCrmClient.RecordType.Value): String = {
    s"https://crm.zoho.com/crm/private/xml/${recordType}/${method}"
  }
}