package app.softnetwork.resource

import app.softnetwork.persistence.model.Timestamped

package object model {

  trait GenericResource extends Timestamped {
    def content: String
    def md5: String
    def mimetype: Option[String]
  }

}
