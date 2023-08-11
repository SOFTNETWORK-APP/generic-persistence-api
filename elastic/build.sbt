Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-elastic"

val elastic = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "org.elasticsearch"      % "elasticsearch"       % Versions.elasticSearch exclude ("org.apache.logging.log4j", "log4j-api"),
  "com.sksamuel.elastic4s" %% "elastic4s-testkit"  % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch"),
  "org.elasticsearch"        % "elasticsearch"     % Versions.elasticSearch % Test exclude ("org.apache.logging.log4j", "log4j-api"),
  "org.apache.logging.log4j" % "log4j-api"         % Versions.log4j % Test,
  "org.apache.logging.log4j" % "log4j-slf4j-impl"  % Versions.log4j % Test,
  "org.apache.logging.log4j" % "log4j-core"        % Versions.log4j % Test
)

val httpComponentsExclusions = Seq(
  ExclusionRule(organization = "org.apache.httpcomponents", name = "httpclient", artifact = "*", configurations = Vector(ConfigRef("test")), crossVersion = CrossVersion.disabled )
)

val guavaExclusion =  ExclusionRule(organization = "com.google.guava", name="guava")

val jest = Seq(
  "io.searchbox" % "jest" % Versions.jest
).map(_.excludeAll(httpComponentsExclusions ++ Seq(guavaExclusion): _*))

libraryDependencies ++= elastic ++ jest ++ Seq(
  "javax.activation" % "activation" % "1.1.1" % Test
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/protobuf"
