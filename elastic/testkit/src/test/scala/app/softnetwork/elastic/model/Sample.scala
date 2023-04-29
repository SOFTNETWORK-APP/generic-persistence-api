package app.softnetwork.elastic.model

import app.softnetwork.persistence._
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.time._

import java.time.Instant

/** Created by smanciot on 12/04/2020.
  */
case class Sample(uuid: String, var createdDate: Instant = now(), var lastUpdated: Instant = now())
    extends Timestamped
