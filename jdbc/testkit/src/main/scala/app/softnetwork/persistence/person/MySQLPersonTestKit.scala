package app.softnetwork.persistence.person

import app.softnetwork.persistence.jdbc.scalatest.MySQLTestKit

trait MySQLPersonTestKit extends JdbcPersonTestKit with MySQLTestKit
