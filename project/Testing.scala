import sbt._
import sbt.Keys._

object Testing {

  import BuildKeys._
  import Configs._

  private lazy val testSettings = Seq(
    fork in Test := true,
    parallelExecution in Test := false
  )

  private lazy val itSettings = inConfig(IntegrationTest)(Defaults.testSettings) ++ Seq(
    fork in IntegrationTest := true,
    parallelExecution in IntegrationTest := false,
    scalaSource in IntegrationTest := baseDirectory.value / "src/it/scala"
  )

  private lazy val e2eSettings = inConfig(EndToEndTest)(Defaults.testSettings) ++ Seq(
    fork in EndToEndTest := true,
    parallelExecution in EndToEndTest := false,
    scalaSource in EndToEndTest := baseDirectory.value / "src/e2e/scala",
    libraryDependencies ++= Seq(
      "org.scoverage" % ("scalac-scoverage-runtime" + "_" + scalaBinaryVersion.value) % "1.1.1" % "test" intransitive(),
      "org.scoverage" % ("scalac-scoverage-plugin" + "_" + scalaBinaryVersion.value) % "1.1.1" % "test" intransitive()
    )
  )

  lazy val settings = testSettings ++ itSettings ++ e2eSettings ++ Seq(
    testAll := (),
    testAll <<= testAll.dependsOn(test in Test),
    testAll <<= testAll.dependsOn(test in IntegrationTest),
    testAll <<= testAll.dependsOn(test in EndToEndTest)
  )
}