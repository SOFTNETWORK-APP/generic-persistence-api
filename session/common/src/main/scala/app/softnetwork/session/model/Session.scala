package app.softnetwork.session.model

import org.softnetwork.session.model.Session

trait SessionDecorator extends SessionDataDecorator[Session] { self: Session =>

  lazy val data: Map[String, String] = kvs

  def withData(data: Map[String, String]): Session = withKvs(data)

}

trait SessionCompanion extends SessionDataCompanion[Session] {
  override def newSession: Session = Session.defaultInstance
}
