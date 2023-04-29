package app.softnetwork.elastic.model

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.persistence.now
import app.softnetwork.time._

import java.time.Instant

case class Binary(
  uuid: String,
  var createdDate: Instant = now(),
  var lastUpdated: Instant = now(),
  content: String,
  md5: String
) extends Timestamped
