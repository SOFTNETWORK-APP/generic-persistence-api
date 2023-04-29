package app.softnetwork.persistence.person.model

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.{generateUUID, now}
import app.softnetwork.time._

import java.time.Instant

case class Person(
  uuid: String,
  name: String,
  birthDate: String,
  createdDate: Instant,
  lastUpdated: Instant
) extends Timestamped

object Person {
  def apply(name: String, birthDate: String): Person =
    apply(
      generateUUID(),
      name,
      birthDate
    )
  def apply(uuid: String, name: String, birthDate: String): Person =
    Person(
      uuid,
      name,
      birthDate,
      now(),
      now()
    )
}
