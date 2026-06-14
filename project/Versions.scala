object Versions {

  val akka = "2.6.20" // TODO 2.6.20 -> 2.8.3

  // Prometheus client_java 1.x — HTTP-route metrics recorded into PrometheusRegistry.defaultRegistry
  // (the shared registry a downstream /metrics endpoint serves). Story 13.6 Phase B.
  val prometheus = "1.7.0"

  val akkaHttp = "10.2.10" // TODO 10.2.10 -> 10.5.3

  val akkaHttpJson4s = "1.39.2" //1.37.0 -> 1.39.2

  val akkaHttpSession = "0.7.1" // 0.7.0 -> 0.7.1

  val tapir = "1.8.5"

  val tapirHttpSession = "0.3.0"

  val akkaPersistenceJdbc = "5.0.4" // TODO 5.0.4 -> 5.2.1

  val akkaManagement = "1.1.4" // TODO 1.1.4 -> 1.4.1

  val postgresql = "42.2.18" // TODO 42.2.18 -> 42.7.7

  val mysql = "8.4.0" // 8.0.33 -> 8.4.0

  val scalatest = "3.2.19" // 3.2.16 -> 3.2.19

  val typesafeConfig = "1.4.3"

  val kxbmap = "0.6.1"

  val jackson = "2.19.0" // 2.12.7 -> 2.19.0

  val json4s = "4.0.6" // 3.6.12 -> 4.0.6

  val scalaLogging = "3.9.2"

  val logback = "1.4.14" // TODO 1.4.14 -> 1.5.6

  // Story 13.7 — structured audit trail. Provides StructuredArguments.kv (used by AuditLog) and, at
  // runtime in the service images, LogstashEncoder + MaskingJsonGeneratorDecorator. 8.1 is the newest
  // line on logback 1.3/1.4 + Jackson 2.x (Java 11+); 9.x requires logback 1.5 + Jackson 3.
  val logstashEncoder = "8.1"

  val slf4j = "1.7.36"

  val log4s = "1.8.2"

  val chill = "0.10.0"

  val elasticSearch = "6.7.2"

  val elastic4s = "6.7.6"

  val jest = "6.3.1"

  val log4j = "2.8.2"

  val slick = "3.3.3"

  val akkaPersistenceCassandra = "1.0.6"

  val flyway = "11.8.1"

  val testContainers = "2.0.4"
}
