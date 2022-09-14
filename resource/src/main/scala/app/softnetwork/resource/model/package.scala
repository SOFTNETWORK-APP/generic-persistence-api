package app.softnetwork.resource

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.utils.ImageTools

package object model {

  trait GenericResource extends Timestamped {
    def content: String
    def md5: String
    def mimetype: Option[String]
    lazy val image: Boolean = ImageTools.isAnImage(mimetype)
    def uri: Option[String]
  }

}
