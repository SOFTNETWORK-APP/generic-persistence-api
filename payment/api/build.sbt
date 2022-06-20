import com.typesafe.sbt.packager.docker._

mainClass in Compile := Some("app.softnetwork.payment.launch.MangoPayApplication")

dockerBaseImage := "java:8"

dockerEntrypoint := Seq(s"${(defaultLinuxInstallLocation in Docker).value}/bin/entrypoint.sh")

dockerExposedVolumes := Seq(
  s"${(defaultLinuxInstallLocation in Docker).value}/conf",
  s"${(defaultLinuxInstallLocation in Docker).value}/logs"
)

dockerExposedPorts := Seq(
  9000,
  5000,
  8558,
  25520
)

scriptClasspath in bashScriptDefines ~= (cp => "../conf" +: cp)

organization := "app.softnetwork.persistence"

name := "persistence-payment-api"
