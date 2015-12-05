resolvers += Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.3")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.9")