package app.softnetwork.persistence.auth.config

/**
  * Created by smanciot on 02/09/2018.
  */
object Notifications {

  case class Config(
                     activation: String,
                     registration: String,
                     accountDisabled: String,
                     resetPassword: String,
                     passwordUpdated: String,
                     principalUpdated: String,
                     resetPasswordCode: Boolean,
                     signature: String,
                     channels: Channels
                   )

  case class Channels(
                       activation: List[String],
                       registration: List[String],
                       accountDisabled: List[String],
                       resetPassword: List[String],
                       passwordUpdated: List[String],
                       principalUpdated: List[String]
                     )
}
