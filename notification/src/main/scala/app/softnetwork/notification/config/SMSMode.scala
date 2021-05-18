package app.softnetwork.notification.config

/**
  * Created by smanciot on 24/08/2018.
  */
object SMSMode {

  case class Config(
                     accessToken: String,
                     baseUrl: String = "https://api.smsmode.com/http",
                     version: String = "1.6",
                     notificationUrl: Option[String] = None,
                     notificationUrlResponse: Option[String] = None,
                     stop: Boolean = false
                   )

  object ResponseType extends Enumeration {
    type ResponseType = Value
    val ACCEPTED = Value(0, "ACCEPTED")
    val INTERNAL_ERROR = Value(31, "INTERNAL_ERROR")
    val AUTHENTICATION_ERROR = Value(32, "AUTHENTICATION_ERROR")
    val INSUFFICIENT_CREDITS = Value(33, "INSUFFICIENT_CREDITS")
    val MISSING_MANDATORY_PARAMETER = Value(35, "MISSING_MANDATORY_PARAMETER")
    val TEMPORARILY_UNAVAILABLE = Value(50, "TEMPORARILY_UNAVAILABLE")
    val NOT_FOUND = Value(61, "NOT_FOUND")
  }

  // code_retour | description | smsID

  object Status extends Enumeration {
    type Status = Value
    val SENT = Value(0, "SENT")
    val IN_PROGRESS = Value(1, "IN_PROGRESS")
    val INTERNAL_ERROR = Value(2, "INTERNAL_ERROR")
    val RECEIVED = Value(11, "RECEIVED")
    val DELIVERED_OPERATOR = Value(13, "DELIVERED_OPERATOR")
    val DELIVERED = Value(14, "DELIVERED")
    val NOT_DELIVERABLE = Value(21, "NOT_DELIVERABLE")
    val REJECTED = Value(22, "REJECTED")
    val INSUFFICIENT_CREDITS = Value(33, "INSUFFICIENT_CREDITS")
    val ROUTING_ERROR = Value(34, "ROUTING_ERROR")
    val RECEIPT_ERROR = Value(35, "RECEIPT_ERROR")
    val MESSAGE_ERROR = Value(36, "MESSAGE_ERROR")
    val EXPIRED_MESSAGE = Value(37, "EXPIRED_MESSAGE")
    val TOO_LONG_MESSAGE = Value(38, "TOO_LONG_MESSAGE")
    val UNDELIVERED = Value(50, "UNDELIVERED")
    val READ = Value(100, "READ")
    val UNREAD = Value(101, "UNREAD")
    val UNDEFINED = Value(999, "UNDEFINED")
  }
}
