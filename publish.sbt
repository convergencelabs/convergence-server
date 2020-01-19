pomIncludeRepository := { _ => false }
publishMavenStyle := true

publishTo := {
  val sonatypeOss = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at sonatypeOss + "content/repositories/snapshots")
  else Some("releases" at sonatypeOss + "service/local/staging/deploy/maven2")
}

(sys.env.get("SONATYPE_USER"), sys.env.get("SONATYPE_PASS")) match {
  case (Some(sonatypeUsername), Some(sonatypePassword)) =>
    credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sonatypeUsername,
      sonatypePassword)
  case _ =>
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")
}
