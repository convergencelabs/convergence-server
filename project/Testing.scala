import sbt._
import sbt.Keys._

object Testing {

  import BuildKeys._
  import Configs._

  val scoverageVersion = "1.3.1"

  private lazy val testSettings = Seq(
    fork in Test := true,
    parallelExecution in Test := false
  )

  private lazy val itSettings = inConfig(IntegrationTest)(Defaults.testSettings) ++ Seq(
    fork in IntegrationTest := true,
    parallelExecution in IntegrationTest := false,
    scalaSource in IntegrationTest := baseDirectory.value / "src/it/scala",
    libraryDependencies ++= Seq(
      "org.scoverage" % ("scalac-scoverage-runtime" + "_" + scalaBinaryVersion.value) % scoverageVersion % "test" intransitive(),
      "org.scoverage" % ("scalac-scoverage-plugin" + "_" + scalaBinaryVersion.value) % scoverageVersion % "test" intransitive()
    )
  )

  private lazy val e2eSettings = inConfig(EndToEndTest)(Defaults.testSettings) ++ Seq(
    fork in EndToEndTest := true,
    parallelExecution in EndToEndTest := false,
    scalaSource in EndToEndTest := baseDirectory.value / "src/e2e/scala",
    libraryDependencies ++= Seq(
      "org.scoverage" % ("scalac-scoverage-runtime" + "_" + scalaBinaryVersion.value) % scoverageVersion % "test" intransitive(),
      "org.scoverage" % ("scalac-scoverage-plugin" + "_" + scalaBinaryVersion.value) % scoverageVersion % "test" intransitive()
    )
  )

  lazy val settings = testSettings ++ itSettings ++ e2eSettings
}
