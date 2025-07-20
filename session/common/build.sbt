organization := "app.softnetwork.session"

name := "session-common"

val tapirHttpSession: Seq[ModuleID] = Seq(
  "app.softnetwork.tapir-http-session" %% "core" % Versions.tapirHttpSession excludeAll
    ExclusionRule(organization = "com.softwaremill")
)

val akkaHttpSession: Seq[ModuleID] = Seq(
  "com.softwaremill.akka-http-session" %% "core" % Versions.akkaHttpSession,
  "com.softwaremill.akka-http-session" %% "jwt"  % Versions.akkaHttpSession
)

libraryDependencies ++= Seq(
  "app.softnetwork.protobuf" %% "scalapb-extensions" % "0.2.0"
) ++ akkaHttpSession ++ tapirHttpSession

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
