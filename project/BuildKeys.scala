import sbt._

object BuildKeys {

  lazy val testAll = TaskKey[Unit]("test-all")
  lazy val ansibleSetVersion = TaskKey[Unit]("ansible-set-version")

  lazy val vagrantFile = SettingKey[File]("vagrant-file")
  lazy val ansibleRoot = SettingKey[File]("ansible-root")
  lazy val ansibleVersionFile = SettingKey[File]("ansible-version-file")
  lazy val ansibleVersionVariable = SettingKey[String]("ansible-version-variable")
}