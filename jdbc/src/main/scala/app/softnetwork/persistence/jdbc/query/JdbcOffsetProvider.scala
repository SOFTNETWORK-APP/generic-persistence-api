package app.softnetwork.persistence.jdbc.query

import akka.Done
import akka.persistence.query.{Offset, Sequence}
import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.query.{EventStream, OffsetProvider}
import mustache.Mustache
import slick.jdbc.JdbcBackend.Session

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait JdbcOffsetProvider extends OffsetProvider with SlickDatabase { _: EventStream =>

  lazy val jdbcEventProcessorOffsets: JdbcEventProcessorOffsets = JdbcEventProcessorOffsets(config)

  lazy val offsetSchema: String = jdbcEventProcessorOffsets.schema

  lazy val offsetTable: String = jdbcEventProcessorOffsets.table

  private[this] implicit lazy val session: Session = withDatabase(_.createSession())

  lazy val includeSchema: Boolean = profile.equals("slick.jdbc.PostgresProfile$")

  override protected def initOffset(): Unit = {
    val metaData = session.metaData
    val tables = metaData.getTables(null, offsetSchema, offsetTable, Array[String]("TABLE"))
    if (!tables.next()) {
      withSource(
        Some(
          Mustache("softnetwork-jdbc-offset.mustache").render(
            Map(
              "offsetSchema"  -> offsetSchema,
              "offsetTable"   -> offsetTable,
              "includeSchema" -> includeSchema
            )
          )
        )
      )
    } else {
      log.info(s"$offsetSchema.$offsetTable already exists")
    }
  }

  /** Read current offset
    *
    * @return
    */
  override protected def readOffset(): Future[Offset] = {
    Try {
      val statement = session.prepareStatement(
        s"SELECT sequence_number FROM ${if (includeSchema) { offsetSchema + "." }
        else { "" }}$offsetTable WHERE event_processor_id=? AND tag=?"
      )
      statement.setString(1, platformEventProcessorId)
      statement.setString(2, platformTag)
      val resultSet = statement.executeQuery()
      val maybeOffset =
        if (resultSet.next()) {
          Some(resultSet.getLong("sequence_number"))
        } else {
          None
        }
      statement.close()
      maybeOffset
    } match {
      case Success(maybeOffset) =>
        maybeOffset match {
          case Some(offset) =>
            log.info(s"SELECT Offset ($platformEventProcessorId, $platformTag) -> $offset")
            Future.successful(Offset.sequence(offset))
          case _ =>
            Try {
              log.info(s"INITIALIZING Offset ($platformEventProcessorId, $platformTag) -> 0")
              val statement = session.prepareStatement(
                s"INSERT INTO ${if (includeSchema) { offsetSchema + "." }
                else { "" }}$offsetTable (event_processor_id, tag, sequence_number) VALUES(?, ?, 0)"
              )
              statement.setString(1, platformEventProcessorId)
              statement.setString(2, platformTag)
              statement.executeUpdate()
            } match {
              case Success(s) =>
                if (s != 1)
                  log.error(
                    s"FAILED TO INSERT Offset ($platformEventProcessorId, $platformTag, 0) -> $s"
                  )
                Future.successful(Offset.sequence(0L))
              case Failure(f) => Future.failed(f)
            }
        }
      case Failure(f) =>
        log.error(
          s"FAILED TO SELECT Offset ($platformEventProcessorId, $platformTag) -> ${f.getMessage}",
          f
        )
        Future.failed(f)
    }
  }

  /** Persist current offset
    *
    * @param offset
    *   - current offset
    * @return
    */
  override protected def writeOffset(offset: Offset): Future[Done] = {
    offset match {
      case Sequence(value) =>
        log.info(s"UPDATING Offset ($platformEventProcessorId, $platformTag) -> $value")
        Try {
          val statement =
            session.prepareStatement(
              s"UPDATE ${if (includeSchema) { offsetSchema + "." }
              else { "" }}$offsetTable set sequence_number=? WHERE event_processor_id=? AND tag=?"
            )
          statement.setLong(1, value)
          statement.setString(2, platformEventProcessorId)
          statement.setString(3, platformTag)
          statement.executeUpdate()
        } match {
          case Success(s) =>
            if (s != 1)
              log.error(
                s"FAILED TO UPDATE Offset ($platformEventProcessorId, $platformTag, $value) -> $s"
              )
            Future.successful(Done)
          case Failure(f) => Future.failed(f)
        }
      case other =>
        Future.failed(
          new Exception(s"JdbcOffsetProvider does not support Offset ${other.getClass}")
        )
    }
  }

  override protected def stopOffset(): Unit = {
    log.info("Stopping Offset")
    session.close()
  }
}
