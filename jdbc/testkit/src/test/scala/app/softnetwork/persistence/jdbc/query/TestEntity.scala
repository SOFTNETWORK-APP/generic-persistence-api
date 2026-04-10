package app.softnetwork.persistence.jdbc.query

import app.softnetwork.persistence.model.Timestamped

import java.time.Instant

/** Test entity mimicking a licensing domain object. */
case class TestEntity(
  uuid: String,
  createdDate: Instant,
  lastUpdated: Instant,
  name: String,
  email: String,
  status: String,
  deleted: Boolean = false
) extends Timestamped
