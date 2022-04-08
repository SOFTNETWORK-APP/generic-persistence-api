import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-session"

val akkaHttpSession: Seq[ModuleID] = Seq(
  "com.softwaremill.akka-http-session" %% "core" % Versions.akkaHttpSession,
  "com.softwaremill.akka-http-session" %% "jwt"  % Versions.akkaHttpSession
)

libraryDependencies ++= akkaHttpSession

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
