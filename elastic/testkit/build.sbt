import app.softnetwork.sbt.build.Versions

Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-elastic-testkit"

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "com.fasterxml.jackson.dataformat"),
  ExclusionRule(organization = "com.fasterxml.jackson.datatype"),
  ExclusionRule(organization = "com.fasterxml.jackson.module")
)

val elastic = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "org.elasticsearch"      % "elasticsearch"       % Versions.elasticSearch exclude ("org.apache.logging.log4j", "log4j-api"),
  "com.sksamuel.elastic4s" %% "elastic4s-testkit"  % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "org.elasticsearch"        % "elasticsearch"     % Versions.elasticSearch exclude ("org.apache.logging.log4j", "log4j-api"),
  "org.apache.logging.log4j" % "log4j-api"         % Versions.log4j,
  "org.apache.logging.log4j" % "log4j-slf4j-impl"  % Versions.log4j,
  "org.apache.logging.log4j" % "log4j-core"        % Versions.log4j,
  "pl.allegro.tech"          % "embedded-elasticsearch" % "2.10.0" excludeAll(jacksonExclusions:_*),
  "org.testcontainers"       % "elasticsearch"     % Versions.testContainers excludeAll(jacksonExclusions:_*)
)

libraryDependencies ++= Seq(
  "org.apache.tika" % "tika-core" % "1.18"
) ++ elastic
