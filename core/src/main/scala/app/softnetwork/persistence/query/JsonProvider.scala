package app.softnetwork.persistence.query

import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.model.{CamelCaseString, StateWrapper, StateWrappertReader, Timestamped}
import app.softnetwork.serialization.{commonFormats, serialization, updateCaseClass}
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s.Formats

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import scala.io.Source
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Success, Try}

trait JsonProvider[T <: Timestamped] extends ExternalPersistenceProvider[T] {
  _: ManifestWrapper[T] =>

  implicit def formats: Formats = commonFormats

  val FORMAT = "yyyy-MM-dd"

  val zoneId: ZoneId = ZoneId.systemDefault()

  lazy val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(FORMAT).withZone(zoneId)

  def outputFolder: String = {
    val config = ConfigFactory.load()
    if (config.hasPath("json-external-processor.output-folder")) {
      config.getString("json-external-processor.output-folder")
    } else {
      System.getProperty("java.io.tmpdir")
    }
  }

  def fileExtension: String = {
    val config = ConfigFactory.load()
    if (config.hasPath("json-external-processor.file-extension")) {
      config.getString("json-external-processor.file-extension")
    } else {
      "json"
    }
  }

  implicit lazy val ct: ClassTag[T] = manifestWrapper.wrapped

  def fileName: String = {
    s"${ct.runtimeClass.getSimpleName $}_${formatter.format(Instant.now())}.$fileExtension"
  }

  def filePath: String = s"$outputFolder/$fileName"

  def reader: StateWrappertReader[T]

  /** Creates the underlying document to the external system
    *
    * @param document
    *   - the document to create
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override def createDocument(document: T)(implicit
    t: ClassTag[T]
  ): Boolean = writeToFile(
    StateWrapper[T](
      document.uuid,
      document.lastUpdated,
      deleted = false,
      Option(document)
    )
  )

  /** Updates the underlying document to the external system
    *
    * @param document
    *   - the document to update
    * @param upsert
    *   - whether or not to create the underlying document if it does not exist in the external
    *     system
    * @param t
    *   - implicit ClassTag for T
    * @return
    *   whether the operation is successful or not
    */
  override def updateDocument(document: T, upsert: Boolean)(implicit
    t: ClassTag[T]
  ): Boolean = writeToFile(
    StateWrapper[T](
      document.uuid,
      document.lastUpdated,
      deleted = false,
      Option(document)
    )
  )

  /** Upsert the underlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to upsert
    * @param data
    *   - the document data
    * @return
    *   whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
    (loadDocument(uuid) match {
      case Some(document: T) =>
        implicit val manifest: Manifest[T] = manifestWrapper.wrapped
        Try(updateCaseClass(document, serialization.read[Map[String, Any]](data))) match {
          case Success(updatedState: T) =>
            Some(updatedState)
          case _ =>
            None
        }
      case _ => None
    }) match {
      case Some(updatedDocument: T) =>
        updateDocument(updatedDocument, upsert = true)
      case _ =>
        writeToFile(
          StateWrapper[T](uuid, Instant.now, deleted = false, None),
          Some(data)
        )
    }
  }

  /** Deletes the underlying document referenced by its uuid to the external system
    *
    * @param uuid
    *   - the uuid of the document to delete
    * @return
    *   whether the operation is successful or not
    */
  override def deleteDocument(uuid: String): Boolean =
    loadDocument(uuid) match {
      case Some(document: T) =>
        writeToFile(
          StateWrapper[T](uuid, Instant.now, deleted = true, Some(document))
        )
      case _ =>
        writeToFile(
          StateWrapper[T](uuid, Instant.now, deleted = true, None)
        )
    }

  /** Load the document referenced by its uuid
    *
    * @param uuid
    *   - the document uuid
    * @param m
    *   - implicit Manifest for T
    * @return
    *   the document retrieved, None otherwise
    */
  override def loadDocument(uuid: String)(implicit m: Manifest[T], formats: Formats): Option[T] = {
    var lastMatchingLine: Option[T] = None
    if (Files.exists(Paths.get(filePath))) {
      val source = Source.fromFile(filePath)
      // Read the file line by line
      for (line <- source.getLines()) {
        Try {
          reader.read(line)
        } match {
          case Success(value) if value.uuid != uuid => // do nothing
          case Success(value) if value.uuid == uuid && value.state.isDefined || value.deleted =>
            if(value.deleted){
              lastMatchingLine = None
            }
            else{
              lastMatchingLine = value.state // return the state
            }
          case _ =>
            val parsed = serialization.read[Map[String, Any]](line)
            val _uuid = parsed.get("uuid") match {
              case Some(u: String) => u
              case _               => ""
            }
            if (uuid == _uuid) {
              val deleted = parsed.get("deleted") match {
                case Some(d) => d.toString.toBoolean
                case _       => false
              }
              if(deleted){
                lastMatchingLine = None
              }
              else{
                parsed.get("state") match {
                  case Some(updated: Map[String, Any]) =>
                    lastMatchingLine match {
                      case Some(l)
                        if updated
                          .get("lastUpdated")
                          .map(lu => Instant.parse(lu.toString))
                          .getOrElse(Instant.MIN)
                          .isAfter(l.lastUpdated) => // update the state
                        Try(updateCaseClass(l, updated)) match {
                          case Success(updated: T) =>
                            lastMatchingLine = Some(updated)
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
              }
            }
        }
      }
      source.close()
    }
    lastMatchingLine
  }

  protected def writeToFile(
    document: StateWrapper[T],
    data: Option[String] = None
  ): Boolean = {
    Try {
      val text = data match {
        case Some(d) =>
          val updated = serialization.read[Map[String, Any]](d)
          val map: Map[String, Any] = Map(
            "uuid"        -> document.uuid,
            "lastUpdated" -> document.lastUpdated,
            "deleted"     -> false,
            "state"       -> updated
          )
          serialization.write(map)
        case _ => document.asJson
      }
      if (!Files.exists(Paths.get(outputFolder))) {
        Files.createDirectories(Paths.get(outputFolder))
      }
      if (!Files.exists(Paths.get(filePath))) {
        Files.createFile(Paths.get(filePath))
      }
      Files.write(
        Paths.get(filePath),
        (text + System.lineSeparator).getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.APPEND
      )
    } match {
      case scala.util.Success(_) => true
      case scala.util.Failure(f) =>
        Console.err.print(f.getMessage, f)
        false
    }
  }

}
