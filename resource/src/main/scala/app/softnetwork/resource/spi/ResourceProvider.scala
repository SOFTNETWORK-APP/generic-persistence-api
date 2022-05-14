package app.softnetwork.resource.spi

import app.softnetwork.utils.ImageTools.ImageSize

import java.nio.file.Path

protected[resource] trait ResourceProvider {

  /**
    *
    * @param uuid - the resource uuid
    * @param content - the optional base64 encoded resource content
    * @param option - the list of resource options
    * @return the optional path associated with this resource
    */
  def loadResource(uuid: String, content: Option[String], option: ResourceOption*): Option[Path]

}

protected[resource] trait ResourceOption

case class SizeOption(size: ImageSize) extends ResourceOption
