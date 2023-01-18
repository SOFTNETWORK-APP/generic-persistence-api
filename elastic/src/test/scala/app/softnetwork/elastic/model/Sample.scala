package app.softnetwork.elastic.model

import java.util.Date

import app.softnetwork.persistence._
import app.softnetwork.persistence.model.Timestamped

/** Created by smanciot on 12/04/2020.
  */
case class Sample(uuid: String, var createdDate: Date = now(), var lastUpdated: Date = now())
    extends Timestamped
