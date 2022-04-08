package app.softnetwork.persistence.auth.model


import org.softnetwork.notification.model.Platform

trait DeviceRegistrationCompanion {
  def apply(regId: String, platform: String, applicationId: Option[String]): DeviceRegistration =
    DeviceRegistration(regId, Platform.fromName(platform).getOrElse(Platform.UNKNOW_PLATFORM), applicationId)
}
