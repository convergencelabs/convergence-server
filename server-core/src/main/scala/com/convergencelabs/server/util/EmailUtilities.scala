package com.convergencelabs.server.util

import com.typesafe.config.Config
import org.apache.commons.mail.SimpleEmail
import org.apache.commons.mail.HtmlEmail
import org.apache.commons.mail.DefaultAuthenticator
import com.convergencelabs.templates
import play.twirl.api.HtmlFormat

object EmailUtilities {

	def createTextEmail(smtpConfig: Config, bodyContent: String): SimpleEmail = {
	  val email = new SimpleEmail()
	  email.setHostName(smtpConfig.getString("host"))
    email.setSmtpPort(smtpConfig.getInt("port"))
    email.setAuthenticator(new DefaultAuthenticator(smtpConfig.getString("username"), smtpConfig.getString("password")))
    email.setFrom(smtpConfig.getString("from-address"))
    email.setMsg(bodyContent)
    email
	}
	
	def createHtmlEmail(smtpConfig: Config, htmlContent: HtmlFormat.Appendable, textContent: String): HtmlEmail = {
	  val email = new HtmlEmail()
	  email.setHostName(smtpConfig.getString("host"))
    email.setSmtpPort(smtpConfig.getInt("port"))
    email.setAuthenticator(new DefaultAuthenticator(smtpConfig.getString("username"), smtpConfig.getString("password")))
    email.setFrom(smtpConfig.getString("from-address"))
    
    val htmlWrapper = templates.email.common.html.wrapper(htmlContent)
    email.setHtmlMsg(htmlWrapper.toString())
    email.setTextMsg(textContent)
    email
	}
	

}