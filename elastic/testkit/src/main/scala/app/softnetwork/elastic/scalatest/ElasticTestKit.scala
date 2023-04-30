package app.softnetwork.elastic.scalatest

import app.softnetwork.concurrent.scalatest.CompletionTestKit
import com.sksamuel.elastic4s.{IndexAndTypes, Indexes}
import com.sksamuel.elastic4s.http.index.admin.RefreshIndexResponse
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticDsl, ElasticProperties}
import com.typesafe.config.{Config, ConfigFactory}
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.slf4j.Logger

import java.util.UUID
import scala.util.{Failure, Success}

/** Created by smanciot on 18/05/2021.
  */
trait ElasticTestKit extends ElasticDsl with CompletionTestKit with BeforeAndAfterAll { _: Suite =>

  def log: Logger

  def elasticVersion: String = "6.7.2"

  def elasticURL: String

  lazy val elasticConfig: Config = ConfigFactory
    .parseString(elasticConfigAsString)
    .withFallback(ConfigFactory.load("softnetwork-elastic.conf"))

  lazy val elasticConfigAsString: String =
    s"""
       |elastic {
       |  credentials {
       |    url = "$elasticURL"
       |  }
       |  multithreaded     = false
       |  discovery-enabled = false
       |}
       |""".stripMargin

  lazy val clusterName: String = s"test-${UUID.randomUUID()}"

  lazy val client: ElasticClient = ElasticClient(ElasticProperties(elasticURL))

  def start(): Unit = ()

  def stop(): Unit = ()

  override def beforeAll(): Unit = {
    start()
    client.execute {
      createIndexTemplate("all_templates", "*").settings(
        Map("number_of_shards" -> 1, "number_of_replicas" -> 0)
      )
    } complete () match {
      case Success(_) => ()
      case Failure(f) => throw f
    }
  }

  override def afterAll(): Unit = {
    client.close()
    stop()
  }

  // Rewriting methods from IndexMatchers in elastic4s with the ElasticClient
  def haveCount(expectedCount: Int): Matcher[String] =
    (left: String) => {
      client.execute(search(left).size(0)) complete () match {
        case Success(s) =>
          val count = s.result.totalHits
          MatchResult(
            count == expectedCount,
            s"Index $left had count $count but expected $expectedCount",
            s"Index $left had document count $expectedCount"
          )
        case Failure(f) => throw f
      }
    }

  def containDoc(expectedId: String): Matcher[String] =
    (left: String) => {
      client.execute(get(expectedId).from(left)) complete () match {
        case Success(s) =>
          val exists = s.result.exists
          MatchResult(
            exists,
            s"Index $left did not contain expected document $expectedId",
            s"Index $left contained document $expectedId"
          )
        case Failure(f) => throw f
      }
    }

  def beCreated(): Matcher[String] =
    (left: String) => {
      client.execute(indexExists(left)) complete () match {
        case Success(s) =>
          val exists = s.result.isExists
          MatchResult(
            exists,
            s"Index $left did not exist",
            s"Index $left exists"
          )
        case Failure(f) => throw f
      }
    }

  def beEmpty(): Matcher[String] =
    (left: String) => {
      client.execute(search(left).size(0)) complete () match {
        case Success(s) =>
          val count = s.result.totalHits
          MatchResult(
            count == 0,
            s"Index $left was not empty",
            s"Index $left was empty"
          )
        case Failure(f) => throw f
      }
    }

  // Copy/paste methos HttpElasticSugar as it is not available yet

  // refresh all indexes
  def refreshAll(): RefreshIndexResponse = refresh(Indexes.All)

  // refreshes all specified indexes
  def refresh(indexes: Indexes): RefreshIndexResponse = {
    client
      .execute {
        refreshIndex(indexes)
      } complete () match {
      case Success(s) => s.result
      case Failure(f) => throw f
    }
  }

  def blockUntilGreen(): Unit = {
    blockUntil("Expected cluster to have green status") { () =>
      client
        .execute {
          clusterHealth()
        } complete () match {
        case Success(s) => s.result.status.toUpperCase == "GREEN"
        case Failure(f) => throw f
      }
    }
  }

  def blockUntil(explain: String)(predicate: () => Boolean): Unit = {
    blockUntil(explain, 16, 200)(predicate)
  }

  def ensureIndexExists(index: String): Unit = {
    client.execute {
      createIndex(index)
    } complete () match {
      case Success(_) => ()
      case Failure(f) =>
        f match {
          case _: ResourceAlreadyExistsException => // Ok, ignore.
          case _: RemoteTransportException       => // Ok, ignore.
          case other                             => throw other
        }
    }
  }

  def doesIndexExists(name: String): Boolean = {
    client
      .execute {
        indexExists(name)
      } complete () match {
      case Success(s) => s.result.isExists
      case _          => false
    }
  }

  def doesAliasExists(name: String): Boolean = {
    client
      .execute {
        aliasExists(name)
      } complete () match {
      case Success(s) => s.result.isExists
      case _          => false
    }
  }

  def deleteIndex(name: String): Unit = {
    if (doesIndexExists(name)) {
      client.execute {
        ElasticDsl.deleteIndex(name)
      } complete () match {
        case Success(_) => ()
        case Failure(f) => throw f
      }
    }
  }

  def truncateIndex(index: String): Unit = {
    deleteIndex(index)
    ensureIndexExists(index)
    blockUntilEmpty(index)
  }

  def blockUntilDocumentExists(id: String, index: String, _type: String): Unit = {
    blockUntil(s"Expected to find document $id") { () =>
      client
        .execute {
          get(id).from(index / _type)
        } complete () match {
        case Success(s) => s.result.exists
        case _          => false
      }
    }
  }

  def blockUntilCount(expected: Long, index: String): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      client.execute {
        search(index).matchAllQuery().size(0)
      } complete () match {
        case Success(s) => expected <= s.result.totalHits
        case Failure(f) => throw f
      }
    }
  }

  def blockUntilCount(expected: Long, indexAndTypes: IndexAndTypes): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      client.execute {
        searchWithType(indexAndTypes).matchAllQuery().size(0)
      } complete () match {
        case Success(s) => expected <= s.result.totalHits
        case Failure(f) => throw f
      }
    }
  }

  /** Will block until the given index and optional types have at least the given number of
    * documents.
    */
  def blockUntilCount(expected: Long, index: String, types: String*): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      client.execute {
        searchWithType(index / types).matchAllQuery().size(0)
      } complete () match {
        case Success(s) => expected <= s.result.totalHits
        case Failure(f) => throw f
      }
    }
  }

  def blockUntilExactCount(expected: Long, index: String, types: String*): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      client
        .execute {
          searchWithType(index / types).size(0)
        } complete () match {
        case Success(s) => expected == s.result.totalHits
        case Failure(f) => throw f
      }
    }
  }

  def blockUntilEmpty(index: String): Unit = {
    blockUntil(s"Expected empty index $index") { () =>
      client
        .execute {
          search(Indexes(index)).size(0)
        } complete () match {
        case Success(s) => s.result.totalHits == 0
        case Failure(f) => throw f
      }
    }
  }

  def blockUntilIndexExists(index: String): Unit = {
    blockUntil(s"Expected exists index $index") { () ⇒
      doesIndexExists(index)
    }
  }

  def blockUntilIndexNotExists(index: String): Unit = {
    blockUntil(s"Expected not exists index $index") { () ⇒
      !doesIndexExists(index)
    }
  }

  def blockUntilAliasExists(alias: String): Unit = {
    blockUntil(s"Expected exists alias $alias") { () ⇒
      doesAliasExists(alias)
    }
  }

  def blockUntilDocumentHasVersion(
    index: String,
    _type: String,
    id: String,
    version: Long
  ): Unit = {
    blockUntil(s"Expected document $id to have version $version") { () =>
      client
        .execute {
          get(id).from(index / _type)
        } complete () match {
        case Success(s) => s.result.version == version
        case Failure(f) => throw f
      }
    }
  }
}
