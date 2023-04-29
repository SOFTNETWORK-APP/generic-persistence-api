package app.softnetwork.persistence.person

import app.softnetwork.persistence.jdbc.scalatest.H2TestKit

trait H2PersonTestKit extends JdbcPersonTestKit with H2TestKit
