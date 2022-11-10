import sbt.Resolver
import Common._
import app.softnetwork.sbt.build._

/////////////////////////////////
// Defaults
/////////////////////////////////

app.softnetwork.sbt.build.Publication.settings

/////////////////////////////////
// Useful aliases
/////////////////////////////////

addCommandAlias("cd", "project") // navigate the projects

addCommandAlias("cc", ";clean;compile") // clean and compile

addCommandAlias("pl", ";clean;publishLocal") // clean and publish locally

addCommandAlias("pr", ";clean;publish") // clean and publish globally

addCommandAlias("pld", ";clean;local:publishLocal;dockerComposeUp") // clean and publish/launch the docker environment

addCommandAlias("dct", ";dockerComposeTest") // navigate the projects

ThisBuild / shellPrompt := prompt

ThisBuild / organization := "app.softnetwork"

name := "generic-persistence-api"

ThisBuild / version := "0.2.5.8"

ThisBuild / scalaVersion := "2.12.11"

ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-target:jvm-1.8")

ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

ThisBuild / resolvers ++= Seq(
  "Softnetwork Server" at "https://softnetwork.jfrog.io/artifactory/releases/",
  "Maven Central Server" at "https://repo1.maven.org/maven2",
  "Typesafe Server" at "https://repo.typesafe.com/typesafe/releases"
)

ThisBuild / libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1"
)

Test / parallelExecution := false

/*val pbSettings = Seq(
  Compile / PB.targets := Seq(
    scalapb.gen() -> crossTarget.value / "protobuf_managed/main"
  )
)*/

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)

lazy val commonTestkit = project.in(file("common/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val core = project.in(file("core"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val coreTestkit = project.in(file("core/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val schema = project.in(file("jdbc/schema"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )

lazy val jdbc = project.in(file("jdbc"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val jdbcTestkit = project.in(file("jdbc/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val akkaJdbc = project.in(file("akka-jdbc"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    jdbc % "compile->compile;test->test;it->it"
  )

lazy val counter = project.in(file("counter"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val scheduler = project.in(file("scheduler"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )

lazy val schedulerTestkit = project.in(file("scheduler/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val schedulerApi = project.in(file("scheduler/api"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(DockerComposePlugin, DockerPlugin, JavaAppPackaging, BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    schema % "compile->compile;test->test;it->it"
  )

lazy val session = project.in(file("session"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val notification = project.in(file("notification"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )

lazy val notificationTestkit = project.in(file("notification/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    notification % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    schedulerTestkit % "compile->compile;test->test;it->it"
  )

lazy val notificationApi = project.in(file("notification/api"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(DockerComposePlugin, DockerPlugin, JavaAppPackaging, BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    notification % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    schedulerApi % "compile->compile;test->test;it->it"
  )

lazy val elasticTestkit = project.in(file("elastic/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    commonTestkit % "compile->compile;test->test;it->it"
  )

lazy val elastic = project.in(file("elastic"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )
  .dependsOn(
    elasticTestkit % "test->test;it->it"
  )

lazy val server = project.in(file("server"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )

lazy val serverTestkit = project.in(file("server/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "compile->compile;test->test;it->it"
  )

lazy val sequence = project.in(file("sequence"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val kv = project.in(file("kv"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    core % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    coreTestkit % "test->test;it->it"
  )

lazy val auth = project.in(file("auth"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings/*, pbSettings*/)
  .enablePlugins(AkkaGrpcPlugin)
  .dependsOn(
    kv % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notification % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val authTestkit = project.in(file("auth/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    auth % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notificationTestkit % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "compile->compile;test->test;it->it"
  )

lazy val authApi = project.in(file("auth/api"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(DockerComposePlugin, DockerPlugin, JavaAppPackaging, BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    auth % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notificationApi % "compile->compile;test->test;it->it"
  )

lazy val resource = project.in(file("resource"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "test->test;it->it"
  )

lazy val resourceTestkit = project.in(file("resource/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    resource % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "compile->compile;test->test;it->it"
  )

lazy val resourceApi = project.in(file("resource/api"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(DockerComposePlugin, DockerPlugin, JavaAppPackaging, BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    resource % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    schema % "compile->compile;test->test;it->it"
  )

lazy val sessionTestkit = project.in(file("session/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    serverTestkit % "compile->compile;test->test;it->it"
  )

lazy val payment = project.in(file("payment"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    kv % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    scheduler % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notification % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    server % "compile->compile;test->test;it->it"
  )

lazy val paymentTestkit = project.in(file("payment/testkit"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    payment % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notificationTestkit % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    sessionTestkit % "compile->compile;test->test;it->it"
  )

lazy val paymentApi = project.in(file("payment/api"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings/*, pbSettings*/)
  .enablePlugins(DockerComposePlugin, DockerPlugin, JavaAppPackaging, BuildInfoPlugin, AkkaGrpcPlugin)
  .dependsOn(
    payment % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    notificationApi % "compile->compile;test->test;it->it"
  )

lazy val root = project.in(file("."))
  .aggregate(
    common,
    commonTestkit,
    core,
    coreTestkit,
    jdbc,
    schema,
    jdbcTestkit,
    akkaJdbc,
    counter,
    session,
    elasticTestkit,
    elastic,
    server,
    serverTestkit,
    sessionTestkit,
    scheduler,
    schedulerTestkit,
    schedulerApi,
    notification,
    notificationTestkit,
    notificationApi,
    sequence,
    kv,
    auth,
    authTestkit,
    authApi,
    resource,
    resourceTestkit,
    resourceApi,
    payment,
    paymentTestkit,
    paymentApi
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

Test / envVars := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
