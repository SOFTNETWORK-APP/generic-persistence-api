package app.softnetwork.persistence.person

import app.softnetwork.elastic.scalatest.EmbeddedElasticTestKit

class PostgresPersonToElasticHandlerSpec
    extends PostgresPersonToElasticTestKit
    with EmbeddedElasticTestKit
