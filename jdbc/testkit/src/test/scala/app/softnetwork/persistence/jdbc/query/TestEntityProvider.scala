package app.softnetwork.persistence.jdbc.query

import app.softnetwork.persistence.ManifestWrapper
import slick.jdbc.{GetResult, JdbcProfile}

import java.time.Instant
import scala.util.Success

/** Test implementation of ColumnMappedJdbcStateProvider, database-agnostic. The concrete test spec
  * mixes in the desired JdbcProfile (H2Profile, PostgresProfile, etc.).
  */
trait TestEntityProvider
    extends ColumnMappedJdbcStateProvider[TestEntity]
    with ManifestWrapper[TestEntity] {
  _: JdbcProfile =>

  override val manifestWrapper: ManifestW = ManifestW()

  override type RowType =
    (
      String,
      Instant,
      Instant,
      String,
      String,
      String,
      Boolean
    ) // uuid, createdDate, lastUpdated, ...
  override type TableType = TestEntities

  import api._

  class TestEntities(tag: Tag) extends Table[RowType](tag, dataset, tableName) {
    def uuid = column[String]("uuid", O.PrimaryKey)
    def createdDate = column[Instant]("created_date")
    def lastUpdated = column[Instant]("last_updated")
    def name = column[String]("name")
    def email = column[String]("email")
    def status = column[String]("status")
    def deleted = column[Boolean]("deleted")
    def * = (uuid, createdDate, lastUpdated, name, email, status, deleted)
  }

  override def tableQuery: TableQuery[TestEntities] =
    TableQuery[TestEntities]

  override def toRow(
    entity: TestEntity,
    deleted: Boolean
  ): RowType =
    (
      entity.uuid,
      entity.createdDate,
      entity.lastUpdated,
      entity.name,
      entity.email,
      entity.status,
      deleted || entity.deleted
    )

  override def fromRow(row: RowType): Option[TestEntity] = row match {
    case (_, _, _, _, _, _, true) => None // soft-deleted
    case (uuid, createdDate, lastUpdated, name, email, status, deleted) =>
      Some(TestEntity(uuid, createdDate, lastUpdated, name, email, status, deleted))
  }

  override def rowUuidColumn(row: TestEntities): Rep[String] = row.uuid

  override implicit def getResult: GetResult[RowType] =
    GetResult { r =>
      (
        r.nextString(),
        r.nextTimestamp().toInstant,
        r.nextTimestamp().toInstant,
        r.nextString(),
        r.nextString(),
        r.nextString(),
        r.nextBoolean()
      )
    }

  // --- Type-safe finder methods ---

  def findByEmail(email: String): Option[TestEntity] = {
    val action =
      tableQuery.filter(r => r.email === email && r.deleted === false).result.headOption
    db.run(action).complete() match {
      case Success(Some(row)) => fromRow(row)
      case _                  => None
    }
  }

  def findByStatus(status: String): List[TestEntity] = {
    val action = tableQuery.filter(r => r.status === status && r.deleted === false).result
    db.run(action).complete() match {
      case Success(rows) => rows.flatMap(fromRow).toList
      case _             => Nil
    }
  }
}
