Test / parallelExecution := false

organization := "app.softnetwork.persistence"

name := "persistence-jdbc"

val akkaPersistenceJdbc = Seq(
  "com.lightbend.akka" %% "akka-persistence-jdbc" % Versions.akkaPersistenceJdbc,
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.slick" %% "slick" % Versions.slick,
  "com.typesafe.slick" %% "slick-hikaricp" % Versions.slick,
  "org.postgresql"       % "postgresql"  % Versions.postgresql,
  "com.mysql" % "mysql-connector-j" % Versions.mysql,
  "org.flywaydb"         % "flyway-core"                % Versions.flyway,
  "org.flywaydb"         % "flyway-database-postgresql"  % Versions.flyway,
  // H2 uses the HSQLDB Flyway plugin — test-scoped because H2 is only used in tests.
  // If H2 is needed outside tests, move this to Compile scope or add it in the consuming project.
  "org.flywaydb"         % "flyway-database-hsqldb"      % Versions.flyway % Test
)

libraryDependencies ++= akkaPersistenceJdbc
