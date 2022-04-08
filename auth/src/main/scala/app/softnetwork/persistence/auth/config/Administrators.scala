package app.softnetwork.persistence.auth.config

/**
  * Created by smanciot on 23/05/2020.
  */
object Administrators {

  case class Administrator(login: String, password: String)

  case class Config(root: Administrator)
}
