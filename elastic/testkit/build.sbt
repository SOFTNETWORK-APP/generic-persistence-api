import app.softnetwork.sbt.build.Versions

parallelExecution in Test := false

organization := "app.softnetwork.persistence"

name := "persistence-elastic-testkit"

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
  "org.apache.logging.log4j" % "log4j-core"        % Versions.log4j
)

libraryDependencies ++= Seq(
  "org.apache.tika" % "tika-core" % "1.18"
) ++ elastic
