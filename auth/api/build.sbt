import com.typesafe.sbt.packager.docker._

mainClass in Compile := Some("app.softnetwork.persistence.auth.api.BasicAccountPostgresLauncher")

dockerBaseImage := "openjdk:8"

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

dockerRepository := Some("softnetwork.jfrog.io/default-docker-local")

scriptClasspath in bashScriptDefines ~= (cp => "../conf" +: cp)

organization := "app.softnetwork.persistence"

name := "persistence-auth-api"
