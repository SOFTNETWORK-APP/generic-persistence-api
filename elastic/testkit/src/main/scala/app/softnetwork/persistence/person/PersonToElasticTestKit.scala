package app.softnetwork.persistence.person

import app.softnetwork.elastic.client.jest.JestClientApi
import app.softnetwork.elastic.persistence.query.ElasticProvider
import app.softnetwork.elastic.scalatest.ElasticTestKit
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.ExternalPersistenceProvider
import app.softnetwork.persistence.schema.Schema
import com.typesafe.config.Config

trait PersonToElasticTestKit extends PersonTestKit with ElasticTestKit { _: Schema =>

  override lazy val externalPersistenceProvider: ExternalPersistenceProvider[Person] =
    new ElasticProvider[Person] with JestClientApi with ManifestWrapper[Person] {
      override def config: Config = PersonToElasticTestKit.this.elasticConfig
      override protected val manifestWrapper: ManifestW = ManifestW()
    }

  override def start(): Unit = {
    super.start()
    initAndJoinCluster()
  }

  override def stop(): Unit = {
    shutdownCluster()
    super.stop()
  }

}
