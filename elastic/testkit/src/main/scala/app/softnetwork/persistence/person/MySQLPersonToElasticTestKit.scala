package app.softnetwork.persistence.person

import app.softnetwork.persistence.jdbc.scalatest.MySQLTestKit

trait MySQLPersonToElasticTestKit extends JdbcPersonToElasticTestKit with MySQLTestKit
