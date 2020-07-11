/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.db.schema.cur

import com.convergencelabs.convergence.server.backend.db.schema.cur.delta.Delta
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.io.Source
import scala.reflect.Manifest
import scala.util.Try

/**
 * The [[SchemaMetaDataRepository]] is responsible for loading the meta data
 * files that describe the available versions of a schema, the installation
 * scripts, and the deltas. The meta data files are loaded from the java
 * classpath. The repository is made up of a index file that specifies
 * the available versions. Each version will have a "full schema"
 * installation script, which can install that version from an empty
 * database; and a version manifest that describes contains meta data
 * about the version along with a listing of all of the deltas required
 * to get to that schema version. The repository also contains a set of delta
 * scripts. The delta scripts are referenced by name by the delta manifests.
 *
 * <pre>
 * {baseClassPath}
 *   - index.yaml
 *   - schemas
 *     - 1.0.yaml
 *     - 2.0.yaml
 *   - versions
 *     - 1.0.yaml
 *     - 2.0.yaml
 *   - deltas
 *     - delta_a.yaml
 *     - delta_a.yaml
 * </pre>
 *
 * @param baseClassPath The base path within the classpath to load schema
 *                      meta data files from.
 */
private[schema] class SchemaMetaDataRepository(baseClassPath: String) {

  import SchemaMetaDataRepository._

  private[this] val indexFile: String = resolveIndexPath(baseClassPath)

  private[this] val mapper = new ObjectMapper(new YAMLFactory())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
    .registerModule(DefaultScalaModule)
    .registerModule(new JavaTimeModule())

  /**
   * Reads the available from the "index.yaml" file.
   *
   * @return The version index information.
   */
  def readVersions(): Either[ReadError, SchemaVersionIndex] = {
    readFileAndDeserialize[SchemaVersionIndex](indexFile).map(_.extracted)
  }

  /**
   * Loads the [[SchemaVersionManifest]] meta data for a specific schema
   * version.
   *
   * @param version The version to load.
   * @return The requested SchemaVersionManifest if successful or an error.
   */
  def readSchemaVersionManifest(version: String): Either[ReadError, SchemaVersionManifest] = {
    readFileAndDeserialize[SchemaVersionManifest](resolveVersionPath(baseClassPath, version))
      .map(d => d.extracted)
  }

  /**
   * Reads the full schema installation scripts. This method returns both the
   * parsed script as the related domain object, as well as returns the raw
   * yaml file that was parsed.
   *
   * @param version The version id of the schema script to get.
   * @return The parsed delta, and the original script, or an error.
   */
  def readFullSchema(version: String): Either[ReadError, InstallDeltaAndScript] = {
    readFileAndDeserialize[Delta](resolveSchemaPath(baseClassPath, version))
      .map(d => InstallDeltaAndScript(version, d.extracted, d.raw))
  }

  /**
   * Reads a single delta from the filesystem.
   *
   * @param id The ids of the deltas to get. The id is essentially the
   *            base file name (minus the ".yaml" extension), plus the
   *            tag specifier for backports.
   * @return The parsed delta and raw yaml, or an error.
   */
  def readDelta(id: UpgradeDeltaId): Either[ReadError, UpgradeDeltaAndScript] = {
    readFileAndDeserialize[Delta](resolveDeltaPath(baseClassPath, id))
      .map(d => UpgradeDeltaAndScript(id, d.extracted, d.raw))
  }

  private[this] def readFileAndDeserialize[T](path: String)(implicit mf: Manifest[T]): Either[ReadError, ExtractedAndRaw[T]] = {
    readFileFromClasspath(path) match {
      case None =>
        Left(FileNotFoundError(path))
      case Some(yaml) =>
        deserialize[T](yaml).map(e => ExtractedAndRaw(e, yaml))
    }
  }

  private[this] def deserialize[T](yaml: String)(implicit mf: Manifest[T]): Either[ParsingError, T] = {
    Try(mapper.readValue(yaml, mf.runtimeClass).asInstanceOf[T])
      .map(Right(_))
      .recover(cause => Left(ParsingError(cause.getMessage)))
      .get
  }

  private[this] def readFileFromClasspath(path: String): Option[String] = {
    Option(getClass.getResourceAsStream(path)) map { in =>
      Source.fromInputStream(in).mkString
    }
  }
}

private[schema] object SchemaMetaDataRepository {
  private val IndexFileName = "index.yaml"
  private val SchemasSubPath = "schemas"
  private val VersionsSubPath = "versions"
  private val DeltasSubPath = "deltas"

  def resolveIndexPath(basePath: String): String = {
    s"$basePath/$IndexFileName"
  }

  def resolveSchemaPath(basePath: String, version: String): String = {
    s"$basePath/$SchemasSubPath/$version.yaml"
  }

  def resolveVersionPath(basePath: String, version: String): String = {
    s"$basePath/$VersionsSubPath/$version.yaml"
  }

  def resolveDeltaPath(basePath: String, deltaId: UpgradeDeltaId): String = {
    s"$basePath/$DeltasSubPath/${deltaId.id}${deltaId.tag.map(t => s"@$t").getOrElse("")}.yaml"
  }

  private final case class ExtractedAndRaw[T](extracted: T, raw: String)

  /**
   * The base trait of errors that arise when parsing schema metadata.
   */
  sealed trait ReadError

  /**
   * Indicates that one of the requested meta data files could not be found.
   * @param path The path of the missing file.
   */
  final case class FileNotFoundError(path: String) extends ReadError

  /**
   * Indicates that there was some issue parsing the yaml file.
   *
   * @param message A message that may contain additional parsing details.
   */
  final case class ParsingError(message: String) extends ReadError

  /**
   * Indicates that an unknown error occurred.
   */
  final case object UnknownError extends ReadError

}
