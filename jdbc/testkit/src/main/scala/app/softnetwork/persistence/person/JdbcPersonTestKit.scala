package app.softnetwork.persistence.person

import app.softnetwork.persistence.jdbc.scalatest.JdbcPersistenceTestKit
import app.softnetwork.persistence.person.query.JdbcPersonProvider
import slick.jdbc.JdbcProfile

trait JdbcPersonTestKit extends PersonTestKit with JdbcPersistenceTestKit with JdbcPersonProvider {
  _: JdbcProfile =>
}
