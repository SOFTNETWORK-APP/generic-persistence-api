package app.softnetwork.resource.model

import app.softnetwork.utils.ImageTools

trait ResourceDecorator {self: GenericResource =>
  lazy val image: Boolean = ImageTools.isAnImage(mimetype)
}
