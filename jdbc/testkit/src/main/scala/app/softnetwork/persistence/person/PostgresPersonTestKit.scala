package app.softnetwork.persistence.person

import app.softnetwork.persistence.jdbc.scalatest.PostgresTestKit

trait PostgresPersonTestKit extends JdbcPersonTestKit with PostgresTestKit
