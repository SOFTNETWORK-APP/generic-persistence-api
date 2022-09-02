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
                     stop: Boolean = false,
                     disabled: Boolean = false
                   )

  object ResponseType extends Enumeration {
    type ResponseType = Value
    val ACCEPTED: SMSMode.ResponseType.Value = Value(0, "ACCEPTED")
    val INTERNAL_ERROR: SMSMode.ResponseType.Value = Value(31, "INTERNAL_ERROR")
    val AUTHENTICATION_ERROR: SMSMode.ResponseType.Value = Value(32, "AUTHENTICATION_ERROR")
    val INSUFFICIENT_CREDITS: SMSMode.ResponseType.Value = Value(33, "INSUFFICIENT_CREDITS")
    val MISSING_MANDATORY_PARAMETER: SMSMode.ResponseType.Value = Value(35, "MISSING_MANDATORY_PARAMETER")
    val TEMPORARILY_UNAVAILABLE: SMSMode.ResponseType.Value = Value(50, "TEMPORARILY_UNAVAILABLE")
    val NOT_FOUND: SMSMode.ResponseType.Value = Value(61, "NOT_FOUND")
  }

  // code_retour | description | smsID

  object Status extends Enumeration {
    type Status = Value
    val SENT: SMSMode.Status.Value = Value(0, "SENT")
    val IN_PROGRESS: SMSMode.Status.Value = Value(1, "IN_PROGRESS")
    val INTERNAL_ERROR: SMSMode.Status.Value = Value(2, "INTERNAL_ERROR")
    val RECEIVED: SMSMode.Status.Value = Value(11, "RECEIVED")
    val DELIVERED_OPERATOR: SMSMode.Status.Value = Value(13, "DELIVERED_OPERATOR")
    val DELIVERED: SMSMode.Status.Value = Value(14, "DELIVERED")
    val NOT_DELIVERABLE: SMSMode.Status.Value = Value(21, "NOT_DELIVERABLE")
    val REJECTED: SMSMode.Status.Value = Value(22, "REJECTED")
    val INSUFFICIENT_CREDITS: SMSMode.Status.Value = Value(33, "INSUFFICIENT_CREDITS")
    val ROUTING_ERROR: SMSMode.Status.Value = Value(34, "ROUTING_ERROR")
    val RECEIPT_ERROR: SMSMode.Status.Value = Value(35, "RECEIPT_ERROR")
    val MESSAGE_ERROR: SMSMode.Status.Value = Value(36, "MESSAGE_ERROR")
    val EXPIRED_MESSAGE: SMSMode.Status.Value = Value(37, "EXPIRED_MESSAGE")
    val TOO_LONG_MESSAGE: SMSMode.Status.Value = Value(38, "TOO_LONG_MESSAGE")
    val UNDELIVERED: SMSMode.Status.Value = Value(50, "UNDELIVERED")
    val READ: SMSMode.Status.Value = Value(100, "READ")
    val UNREAD: SMSMode.Status.Value = Value(101, "UNREAD")
    val UNDEFINED: SMSMode.Status.Value = Value(999, "UNDEFINED")
  }
}
