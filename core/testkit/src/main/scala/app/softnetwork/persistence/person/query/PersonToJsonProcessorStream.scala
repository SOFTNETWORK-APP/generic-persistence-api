package app.softnetwork.persistence.person.query

import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.query.{InMemoryJournalProvider, InMemoryOffsetProvider, JsonProvider}

import java.nio.file.{Files, Paths}

trait PersonToJsonProcessorStream
    extends PersonToExternalProcessorStream
    with InMemoryJournalProvider
    with InMemoryOffsetProvider
    with JsonProvider[Person] {
  override protected def init(): Unit = {
    val path = Paths.get(filePath)
    if (Files.exists(path)) {
      Files.delete(path)
    }
  }
}
