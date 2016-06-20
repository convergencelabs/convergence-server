package com.convergencelabs.server.testkit

import java.io.File
import scala.collection.JavaConverters._

object FileUtils {

  def deleteDirectory(path: File): Boolean = {
    if (path.exists()) {
      val files = path.listFiles().toList
      files.foreach { file =>
        if (file.isDirectory()) {
          deleteDirectory(file)
        } else {
          file.delete()
        }
      }
    }

    path.delete()
  }
}