/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

import java.nio.file.{Files, Paths}

import sbt.Keys.managedClasspath
import sbt.{AttributeKey, Def, File, ModuleID, taskKey, _}

/**
 * This is a simple SBT plugin that will copy OrientDB plugins into the proper
 * location to enable the Convergence Dev Server's embedded OrientDB server to
 * be fully functional.
 */
object OrientDBPlugin extends AutoPlugin {

  object autoImport {
    val orientDbPlugins = taskKey[Unit]("Copies the OrientDB plugins to the target directory")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[Task[Unit]]] = Seq(
    orientDbPlugins := {
      val pluginsPath = Paths.get("target/orientdb/plugins")
      val pluginsDir = pluginsPath.toFile

      // Delete any existing plugins.
      if (pluginsDir.exists()) {
        println("Deleting existing Orient DB Plugins")
        val allContents = pluginsDir.listFiles
        if (allContents != null) for (file <- allContents) {
          if (file.isFile) {
            file.delete()
          }
        }
      } else {
        println("Creating OrientDB Plugins directory")
        Files.createDirectories(pluginsPath)
      }

      val orientDbStudio = "orientdb-studio"
      val cp = (managedClasspath in Compile).value
      val matches = cp.toList.filter { f =>
        val moduleId = f.metadata.get(AttributeKey[ModuleID]("moduleID"))
        moduleId.exists(_.name.contains(orientDbStudio))
      }

      matches match {
        case studio :: Nil =>
          println("Copying studio plugin: " + studio.data)
          val source = studio.data
          val target = new File(pluginsDir, source.getName)
          if (!target.exists) {
            Files.copy(source.toPath, target.toPath)
          }
        case Nil =>
          throw new RuntimeException("OrientDB Studio jar not found")
        case _ =>
          throw new RuntimeException("Multiple OrientDB Studio jars found on the classpath")
      }
    }
  )
}
