package app.softnetwork.api.server.scalatest

import app.softnetwork.api.server.Server
import app.softnetwork.persistence.scalatest.PersistenceTestKit

import java.net.ServerSocket

trait ServerTestKit extends Server { _: PersistenceTestKit =>

  override lazy val interface: String = hostname

  override lazy val port: Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }

  lazy val serverConfig: String =
    s"""
       |softnetwork.api.server.interface = $interface
       |softnetwork.api.server.port = $port
       |""".stripMargin
}
