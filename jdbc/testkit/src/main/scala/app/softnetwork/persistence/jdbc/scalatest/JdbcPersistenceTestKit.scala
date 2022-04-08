package app.softnetwork.persistence.jdbc.scalatest

import app.softnetwork.persistence.jdbc.query.JdbcSchemaProvider
import app.softnetwork.persistence.scalatest.PersistenceTestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite

/**
  * Created by smanciot on 14/05/2021.
  */
trait JdbcPersistenceTestKit extends PersistenceTestKit {_: Suite with JdbcSchemaProvider =>

  def slick: String

  lazy val slickConfig: Config = ConfigFactory.parseString(slick)

  override lazy val cfg: Config = slickConfig

  override lazy val config: Config =
    akkaConfig
      .withFallback(slickConfig)
      .withFallback(ConfigFactory.load("softnetwork-jdbc-persistence.conf"))
      .withFallback(ConfigFactory.load())

  override def beforeAll(): Unit = {
    initSchema()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    db.close()
  }

}
