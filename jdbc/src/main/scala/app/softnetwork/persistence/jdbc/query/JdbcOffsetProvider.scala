package app.softnetwork.persistence.jdbc.query

import akka.Done
import akka.persistence.query.{Offset, Sequence}
import app.softnetwork.persistence.jdbc.db.SlickDatabase
import app.softnetwork.persistence.query.OffsetProvider
import mustache.Mustache
import slick.jdbc.JdbcBackend.Session

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait JdbcOffsetProvider extends OffsetProvider with SlickDatabase {

  lazy val jdbcEventProcessorOffsets: JdbcEventProcessorOffsets = JdbcEventProcessorOffsets(config)

  lazy val offsetSchema: String = jdbcEventProcessorOffsets.schema

  lazy val offsetTable: String = jdbcEventProcessorOffsets.table

  private[this] implicit lazy val session: Session = withDatabase(_.createSession())

  classicSystem.registerOnTermination(() => session.close())

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
      val sequenceNumber =
        if (resultSet.next()) {
          Some(resultSet.getLong("sequence_number"))
        } else {
          None
        }
      statement.close()
      sequenceNumber
    } match {
      case Success(sequenceNumber) =>
        sequenceNumber match {
          case Some(s) =>
            log.info(s"SELECT Offset ($platformEventProcessorId, $platformTag) -> $s")
            Future.successful(Offset.sequence(s))
          case _ =>
            Try {
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
                Future.successful(startOffset())
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
          new Exception(s"JdbcJournalProvider does not support Offset ${other.getClass}")
        )
    }
  }

}
