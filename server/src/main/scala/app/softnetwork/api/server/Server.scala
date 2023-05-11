package app.softnetwork.api.server

import app.softnetwork.api.server.config.ServerSettings.{Interface, Port}

trait Server {

  def interface: String = Interface

  def port: Int = Port

}
