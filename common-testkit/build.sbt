import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-common-testkit"

val scalatest = Seq(
  "org.scalatest" %% "scalatest" % Versions.scalatest
)

val dockerTestKit = Seq(
  "com.whisk" %% "docker-testkit-scalatest"        % Versions.dockerTestKit exclude ("org.scalatest", "scalatest"),
  "com.whisk" %% "docker-testkit-impl-docker-java" % Versions.dockerTestKit exclude ("org.apache.httpcomponents", "httpclient"),
  "com.whisk" %% "docker-testkit-config"           % Versions.dockerTestKit,
  "com.whisk" %% "docker-testkit-impl-spotify"     % Versions.dockerTestKit
)

libraryDependencies ++= scalatest ++ dockerTestKit
