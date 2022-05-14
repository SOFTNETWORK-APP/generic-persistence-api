package app.softnetwork.resource.model

import app.softnetwork.utils.ImageTools

trait ResourceDecorator {self: Resource =>
  lazy val image: Boolean = ImageTools.isAnImage(mimetype)
}
