package app.softnetwork.session.model

import app.softnetwork.persistence.model.ProtobufDomainObject

/** Created by smanciot on 29/04/2021.
  */
trait SessionData extends ProtobufDomainObject {
  def data: Map[String, String]

  def refreshable: Boolean
}
