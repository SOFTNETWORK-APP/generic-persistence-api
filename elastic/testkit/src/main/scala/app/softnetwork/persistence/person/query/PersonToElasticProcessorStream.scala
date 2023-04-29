package app.softnetwork.persistence.person.query

import app.softnetwork.elastic.client.jest.JestClientApi
import app.softnetwork.elastic.persistence.query.ElasticProvider
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.{JournalProvider, OffsetProvider}

trait PersonToElasticProcessorStream
    extends PersonToExternalProcessorStream
    with ElasticProvider[Person]
    with JestClientApi
    with ManifestWrapper[Person] { _: JournalProvider with OffsetProvider =>
  override protected val manifestWrapper: ManifestW = ManifestW()
}
