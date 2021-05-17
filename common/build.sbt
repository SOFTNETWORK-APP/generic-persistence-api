import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-common"

val configDependencies = Seq(
  "com.typesafe"      % "config"   % Versions.typesafeConfig,
  "com.github.kxbmap" %% "configs" % Versions.kxbmap
)

val jackson = Seq(
  "com.fasterxml.jackson.core"   % "jackson-databind"          % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-core"              % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-annotations"       % Versions.jackson,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % Versions.jackson
)

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "org.codehaus.jackson")
)

val json4s = Seq(
  "org.json4s" %% "json4s-jackson" % Versions.json4s,
  "org.json4s" %% "json4s-ext"     % Versions.json4s
).map(_.excludeAll(jacksonExclusions: _*)) ++ jackson

val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging,
  "org.log4s"                  %% "log4s"         % Versions.log4s,
  "org.slf4j"                  % "slf4j-api"      % Versions.slf4j,
  "org.slf4j"                  % "jcl-over-slf4j" % Versions.slf4j,
  "org.slf4j"                  % "jul-to-slf4j"   % Versions.slf4j
)

val scalatest = Seq(
  "org.scalatest" %% "scalatest" % Versions.scalatest  % Test
)

val dockerTestKit = Seq(
  "com.whisk" %% "docker-testkit-scalatest"        % Versions.dockerTestKit % Test,
  "com.whisk" %% "docker-testkit-impl-docker-java" % Versions.dockerTestKit % Test exclude ("org.apache.httpcomponents", "httpclient"),
  "com.whisk" %% "docker-testkit-config"           % Versions.dockerTestKit % Test,
  "com.whisk" %% "docker-testkit-impl-spotify"     % Versions.dockerTestKit % Test
)

dependencyOverrides ++= jackson.toSet

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.10",
  "org.apache.commons" % "commons-text" % "1.4" excludeAll ExclusionRule(organization = "org.apache.commons", name = "commons-lang3")
) ++ configDependencies ++ json4s ++ logging ++ scalatest ++ dockerTestKit
