package app.softnetwork.elastic.client

import app.softnetwork.elastic.client.jest.JestProvider
import app.softnetwork.elastic.model.{Binary, Sample}
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.person.model.Person
import com.typesafe.config.Config
import io.searchbox.client.JestClient

object JestProviders {

  class PersonProvider(es: Config) extends JestProvider[Person] with ManifestWrapper[Person] {
    override protected val manifestWrapper: ManifestW = ManifestW()

    override lazy val config: Config = es

    implicit lazy val jestClient: JestClient =
      apply(elasticConfig.credentials, elasticConfig.multithreaded)
  }

  class SampleProvider(es: Config) extends JestProvider[Sample] with ManifestWrapper[Sample] {
    override protected val manifestWrapper: ManifestW = ManifestW()

    override lazy val config: Config = es

    implicit lazy val jestClient: JestClient =
      apply(elasticConfig.credentials, elasticConfig.multithreaded)
  }

  class BinaryProvider(es: Config) extends JestProvider[Binary] with ManifestWrapper[Binary] {
    override protected val manifestWrapper: ManifestW = ManifestW()

    override lazy val config: Config = es

    implicit lazy val jestClient: JestClient =
      apply(elasticConfig.credentials, elasticConfig.multithreaded)
  }
}
