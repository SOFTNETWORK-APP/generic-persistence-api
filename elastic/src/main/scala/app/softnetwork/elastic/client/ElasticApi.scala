package app.softnetwork.elastic.client

import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import _root_.akka.stream.{Materializer, FlowShape}
import akka.stream.scaladsl._

import com.google.gson.{JsonObject, Gson, JsonElement}

import com.typesafe.scalalogging.StrictLogging

import io.searchbox.indices.mapping.PutMapping
import app.softnetwork.persistence.ManifestWrapper

import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.serialization._
import app.softnetwork.elastic.config.Settings
import app.softnetwork.elastic.config.Settings.ElasticConfig

import app.softnetwork.elastic.persistence.query.ElasticProvider
import app.softnetwork.elastic.sql.ElasticQuery

import ResultHandler._

import io.searchbox.action.{Action, BulkableAction}
import io.searchbox.client.config.HttpClientConfig
import io.searchbox.client.{JestClient, JestClientFactory, JestResult, JestResultHandler}
import io.searchbox.core._
import io.searchbox.indices.aliases.{AddAliasMapping, ModifyAliases}
import io.searchbox.indices.settings.UpdateSettings
import io.searchbox.indices._
import io.searchbox.params.Parameters

import org.apache.http.HttpHost

import org.json4s.{Formats, DefaultFormats}
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

import scala.concurrent.{Future, ExecutionContext, Promise, Await}
import scala.concurrent.duration.Duration

import scala.language.implicitConversions
import scala.reflect.ClassTag

import scala.util.{Try, Success, Failure}

/**
  * Created by smanciot on 28/06/2018.
  */
trait ElasticApi[T <: Timestamped] extends JestProvider[T]
  with IndicesApi
  with UpdateSettingsApi
  with AliasApi
  with MappingApi
  with CountApi
  with SearchApi
  with IndexApi
  with UpdateApi
  with GetApi
  with BulkApi
  with DeleteApi
  with RefreshApi
  with FlushApi
  with JestClientCompanion {_: ManifestWrapper[T] =>
}

trait JestClientCompanion extends StrictLogging {

  protected def credentials: Option[ElasticCredentials] = None

  private[this] lazy val elasticConfig: ElasticConfig = Settings.config.get

  private[this] var jestClient: Option[InnerJestClient] = None

  private[this] val factory = new JestClientFactory()

  private[this] var httpClientConfig: HttpClientConfig = _

  private[this] class InnerJestClient(private var _jestClient: JestClient) extends JestClient {
    private[this] var nbFailures: Int = 0

    override def shutdownClient(): Unit = {
      close()
    }

    private def checkClient(): Unit = {
      Option(_jestClient) match {
        case None =>
          factory.setHttpClientConfig(httpClientConfig)
          _jestClient = Try(factory.getObject) match {
            case Success(s) =>
              s
            case Failure(f) =>
              logger.error(f.getMessage, f)
              throw f
          }
        case _ =>
      }
    }

    override def executeAsync[J <: JestResult](clientRequest: Action[J],
                                               jestResultHandler: JestResultHandler[_ >: J]): Unit = {
      Try(checkClient())
      Option(_jestClient) match {
        case Some(s) => s.executeAsync[J](clientRequest, jestResultHandler)
        case _       =>
          close()
          jestResultHandler.failed(new Exception("JestClient not initialized"))
      }
    }

    override def execute[J <: JestResult](clientRequest: Action[J]): J = {
      Try(checkClient())
      Option(_jestClient) match {
        case Some(j) => Try(j.execute[J](clientRequest)) match {
          case Success(s) =>
            nbFailures = 0
            s
          case Failure(f) =>
            f match {
              case e: IOException =>
                nbFailures += 1
                logger.error(e.getMessage, e)
                close()
                if(nbFailures < 10){
                  Thread.sleep(1000 * nbFailures)
                  execute(clientRequest)
                }
                else {
                  throw f
                }
              case e: IllegalStateException =>
                nbFailures += 1
                logger.error(e.getMessage, e)
                close()
                if(nbFailures < 10){
                  Thread.sleep(1000 * nbFailures)
                  execute(clientRequest)
                }
                else {
                  throw f
                }
              case _      =>
                close()
                throw f
            }
        }
        case _      =>
          close()
          throw new Exception("JestClient not initialized")
      }
    }

    override def setServers(servers: util.Set[String]): Unit = {
      Try(checkClient())
      Option(_jestClient).foreach(_.setServers(servers))
    }

    override def close(): Unit = {
      Option(_jestClient).foreach(_.close())
      _jestClient = null
    }
  }

  private[this] def getHttpHosts(esUrl: String): Set[HttpHost] = {
    esUrl.split(",").map(u => {
      val url = new java.net.URL(u)
      new HttpHost(url.getHost, url.getPort, url.getProtocol)
    }).toSet
  }

  def apply(): JestClient = {
    apply(
      credentials.getOrElse(elasticConfig.credentials),
      multithreaded = elasticConfig.multithreaded,
      discoveryEnabled = elasticConfig.discoveryEnabled
    )
  }

  def apply(esCredentials: ElasticCredentials,
            multithreaded: Boolean = true,
            timeout: Int = 60000,
            discoveryEnabled: Boolean = false,
            discoveryFrequency: Long = 60L,
            discoveryFrequencyTimeUnit: TimeUnit = TimeUnit.SECONDS): JestClient = {
    jestClient match {
      case Some(s) => s
      case None =>
        httpClientConfig = new HttpClientConfig.Builder(esCredentials.url)
          .defaultCredentials(esCredentials.username, esCredentials.password)
          .preemptiveAuthTargetHosts(getHttpHosts(esCredentials.url).asJava)
          .multiThreaded(multithreaded)
          .discoveryEnabled(discoveryEnabled)
          .discoveryFrequency(discoveryFrequency, discoveryFrequencyTimeUnit)
          .connTimeout(timeout)
          .readTimeout(timeout)
          .build()
        factory.setHttpClientConfig(httpClientConfig)
        jestClient = Some(new InnerJestClient(factory.getObject))
        jestClient.get
    }
  }

}

trait IndicesApi {_: JestClientCompanion =>
  val defaultSettings =
    """
      |{
      |  "index": {
      |    "max_ngram_diff": "20",
      |    "mapping" : {
      |      "total_fields" : {
      |        "limit" : "2000"
      |      }
      |    },
      |    "analysis": {
      |      "analyzer": {
      |        "ngram_analyzer": {
      |          "tokenizer": "ngram_tokenizer",
      |          "filter": [
      |            "lowercase",
      |            "asciifolding"
      |          ]
      |        },
      |        "search_analyzer": {
      |          "type": "custom",
      |          "tokenizer": "standard",
      |          "filter": [
      |            "lowercase",
      |            "asciifolding"
      |          ]
      |        }
      |      },
      |      "tokenizer": {
      |        "ngram_tokenizer": {
      |          "type": "ngram",
      |          "min_gram": 1,
      |          "max_gram": 20,
      |          "token_chars": [
      |            "letter",
      |            "digit"
      |          ]
      |        }
      |      }
      |    }
      |  }
      |}
    """.stripMargin

  def createIndex(index: String, settings: String = defaultSettings) = apply().execute(new CreateIndex.Builder(index).settings(settings).build()).isSucceeded
  def deleteIndex(index: String) = apply().execute(new DeleteIndex.Builder(index).build()).isSucceeded
  def closeIndex(index: String) = apply().execute(new CloseIndex.Builder(index).build()).isSucceeded
  def openIndex(index: String) = apply().execute(new OpenIndex.Builder(index).build()).isSucceeded
}

trait AliasApi {_: JestClientCompanion =>
  def addAlias(index: String, alias: String) = {
    apply().execute(
      new ModifyAliases.Builder(
        new AddAliasMapping.Builder(index, alias).build()
      ).build()
    ).isSucceeded
  }
}

trait UpdateSettingsApi extends IndicesApi {_: JestClientCompanion =>
  def toggleRefresh(index: String, enable: Boolean): Unit = {
    val source =
      if (!enable) """{"index" : {"refresh_interval" : -1} }""" else """{"index" : {"refresh_interval" : "1s"} }"""
    apply().execute(new UpdateSettings.Builder(source).addIndex(index).build())
  }

  def setReplicas(index: String, replicas: Int): Unit = {
    val source = s"""{"index" : {"number_of_replicas" : $replicas} }"""
    apply().execute(new UpdateSettings.Builder(source).addIndex(index).build())
  }

  def updateSettings(index: String, settings: String = defaultSettings) =
    closeIndex(index) &&
      apply().execute(new UpdateSettings.Builder(settings).addIndex(index).build()).isSucceeded &&
      openIndex(index)
}

trait MappingApi {_: JestClientCompanion =>
  def setMapping(index: String, `type`: String, mapping: String) =
    apply().execute(new PutMapping.Builder(index, `type`, mapping).build()).isSucceeded
}

trait RefreshApi {_: JestClientCompanion =>
  def refresh(index: String) = apply().execute(new Refresh.Builder().addIndex(index).build()).isSucceeded
}

trait FlushApi {_: JestClientCompanion =>
  def flush(index: String, force: Boolean = true, wait: Boolean = true) = apply().execute(
    new Flush.Builder().addIndex(index).force(force).waitIfOngoing(wait).build()
  ).isSucceeded
}

trait IndexApi {_: JestClientCompanion =>
  def index[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit u: ClassTag[U], formats: Formats): String = {
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    val _index = index.getOrElse(_type)
    val source = serialization.write[U](entity)
    val id = entity.uuid
    this.index(_index, _type, id, source)
  }

  def indexAsync[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit u: ClassTag[U], ec: ExecutionContext, formats: Formats): Future[String] = {
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    val _index = index.getOrElse(_type)
    val source = serialization.write[U](entity)
    val id = entity.uuid
    this.indexAsync(_index, _type, id, source)
  }

  def index(index: String, `type`: String, id: String, source: String): String = {
    apply().execute(
      new Index.Builder(source).index(index).`type`(`type`).id(id).build()
    ).getId
  }

  def indexAsync( index: String, `type`: String, id: String, source: String)(
    implicit ec: ExecutionContext): Future[String] = {
    val promise: Promise[String] = Promise()
    apply().executeAsyncPromise(
      new Index.Builder(source).index(index).`type`(`type`).id(id).build()
    ) onComplete {
      case Success(s) => promise.success(s.getId)
      case Failure(f) => promise.failure(f)
    }
    promise.future
  }

}

trait UpdateApi {_: JestClientCompanion =>
  def update[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None, upsert: Boolean = true)(
    implicit u: ClassTag[U], formats: Formats): String = {
    val source = serialization.write[U](entity)
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    val _index = index.getOrElse(_type)
    val id = entity.uuid
    this.update(_index, _type, id, source, upsert)
  }

  def updateAsync[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None, upsert: Boolean = true)(
    implicit u: ClassTag[U], ec: ExecutionContext, formats: Formats): Future[String] = {
    val source = serialization.write[U](entity)
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    val _index = index.getOrElse(_type)
    val id = entity.uuid
    this.updateAsync(_index, _type, id, source, upsert)
  }

  def update(index: String, `type`: String, id: String, source: String, upsert: Boolean): String = {
    apply().execute(
      new Update.Builder(
        if(upsert)
          docAsUpsert(source)
        else
          source
      ).index(index).`type`(`type`).id(id).build()
    ).getId
  }

  def updateAsync(index: String, `type`: String, id: String, source: String, upsert: Boolean)(
    implicit ec: ExecutionContext): Future[String] = {
    val promise: Promise[String] = Promise()
    apply().executeAsyncPromise(
      new Update.Builder(
        if(upsert)
          docAsUpsert(source)
        else
          source
      ).index(index).`type`(`type`).id(id).build()
    ) onComplete {
      case Success(s) => promise.success(s.getId)
      case Failure(f) => promise.failure(f)
    }
    promise.future
  }

}

trait DeleteApi {_: JestClientCompanion =>
  def delete[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit u: ClassTag[U]): Boolean = {
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    apply().execute(
      new Delete.Builder(entity.uuid).index(
        index.getOrElse(
          _type
        )
      ).`type`(_type).build()
    ).isSucceeded
  }

  def deleteAsync[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit u: ClassTag[U], ec: ExecutionContext): Future[Boolean] = {
    val promise: Promise[Boolean] = Promise()
    val _type  = `type`.getOrElse(u.runtimeClass.getSimpleName.toLowerCase)
    apply().executeAsyncPromise(
      new Delete.Builder(entity.uuid).index(
        index.getOrElse(
          _type
        )
      ).`type`(_type).build()
    ) onComplete {
      case Success(s) =>
        if(s.isSucceeded)
          promise.success(true)
        else
          promise.failure(new Exception(s.getErrorMessage))
      case Failure(f) => promise.failure(f)
    }
    promise.future
  }

  def delete(uuid: String, index: String, `type`: String): Boolean = {
    val result = apply().execute(
      new Delete.Builder(uuid).index(index).`type`(`type`).build()
    )
    val res = result.isSucceeded
    if(!res){
      logger.error(result.getErrorMessage)
    }
    res
  }

  def deleteAsync(uuid: String, index: String, `type`: String)(
    implicit ec: ExecutionContext): Future[Boolean] = {
    Future(delete(uuid, index, `type`))
  }

}

trait BulkApi {_: JestClientCompanion with RefreshApi =>
  def prepareBulk(index: String): Unit = {
    val source =
      """{"index" : {"refresh_interval" : "-1", "number_of_replicas" : 0} }"""
    apply().execute(new UpdateSettings.Builder(source).addIndex(index).build())
  }

  /**
    * +----------+
    * |          |
    * |  Source  |  items: Iterator[D]
    * |          |
    * +----------+
    *      |
    *      v
    * +----------+
    * |          |
    * |transform | BulkableAction
    * |          |
    * +----------+
    *      |
    *      v
    * +----------+
    * |          |
    * | settings | Update elasticsearch settings (refresh and replicas)
    * |          |
    * +----------+
    *      |
    *      v
    * +----------+
    * |          |
    * |  group   |
    * |          |
    * +----------+
    *      |
    *      v
    * +----------+        +----------+
    * |          |------->|          |
    * |  balance |        |   bulk   |
    * |          |------->|          |
    * +----------+        +----------+
    * |    |
    * |    |
    * |    |
    * +---------+            |    |
    * |         |<-----------'    |
    * |  merge  |                 |
    * |         |<----------------'
    * +---------+
    *      |
    *      v
    * +----------+
    * |          |
    * | result   | BulkResult
    * |          |
    * +----------+
    *      |
    *      v
    * +----------+
    * |          |
    * |   Sink   | indices: Set[String]
    * |          |
    * +----------+
    *
    * Asynchronously bulk items in Elasticsearch
    *
    * @param items         the items to index
    * @param toDocument    to transform documents to json strings
    * @param idKey         the key where to find the id for ES
    * @param suffixDateKey the key where to find a suffix date for indexes
    * @param update        whether to upsert or not the items
    * @param parentIdKey   the key where to find the parent id for ES
    * @param bulkOptions   options of bulk
    * @param system        actor system
    * @tparam D the original type of the document
    * @return the indexes on which some documents have been indexed
    */
  def bulk[D](items: Iterator[D],
              toDocument: D => String,
              idKey: Option[String] = None,
              suffixDateKey: Option[String] = None,
              suffixDatePattern: Option[String] = None,
              update: Option[Boolean] = None,
              delete: Option[Boolean] = None,
              parentIdKey: Option[String] = None)(implicit bulkOptions: BulkOptions, system: ActorSystem): Set[String] = {

    implicit val materializer = Materializer(system)

    import GraphDSL.Implicits._

    val source = Source.fromIterator(() => items)
    val sink   = Sink.fold[Set[String], Set[String]](Set.empty[String])(_ ++ _)

    val g = Flow.fromGraph(GraphDSL.create() { implicit b =>
      val transform =
        b.add(Flow[D].map(toBulkAction(toDocument, idKey, suffixDateKey, suffixDatePattern, update, delete, parentIdKey, _)))

      val settings = b.add(BulkSettings(this, bulkOptions.disableRefresh))

      val group = b.add(Flow[BulkableAction[DocumentResult]].named("group").grouped(bulkOptions.maxBulkSize).map {
        items =>
          logger.info(s"Preparing to write batch of ${items.size}...")
          items
      })

      val parallelism = Math.max(1, bulkOptions.balance)

      def bulk() =
        b.add(
          Flow[Seq[BulkableAction[DocumentResult]]]
            .named("bulk")
            .mapAsyncUnordered[BulkResult](parallelism)(items => {
            logger.info(s"Starting to write batch of ${items.size}...")
            val init = new Bulk.Builder().defaultIndex(bulkOptions.index).defaultType(bulkOptions.documentType)
            val bulkQuery = items.foldLeft(init) { (current, query) =>
              current.addAction(query)
            }
            apply().executeAsyncPromise(bulkQuery.build())
          }))

      val result = b.add(
        Flow[BulkResult]
          .named("result")
          .map(result => {
            val items   = result.getItems
            val indices = items.asScala.map(_.index).toSet
            logger.info(s"Finished to write batch of ${items.size} within ${indices.mkString(",")}.")
            indices
          }))

      if (parallelism > 1) {
        val balancer = b.add(Balance[Seq[BulkableAction[DocumentResult]]](parallelism))

        val merge = b.add(Merge[BulkResult](parallelism))

        transform ~> settings ~> group ~> balancer

        1 to parallelism foreach { _ =>
          balancer ~> bulk() ~> merge
        }

        merge ~> result
      } else {
        transform ~> settings ~> group ~> bulk() ~> result
      }

      FlowShape(transform.in, result.out)
    })

    val future = source.via(g).toMat(sink)(Keep.right).run()

    val indices = Await.result(future, Duration.Inf)
    indices.foreach(refresh)
    indices
  }

  private[this] def toElasticDocument(
                         document: String,
                         idKey: Option[String],
                         suffixDateKey: Option[String],
                         suffixDatePattern: Option[String],
                         parentIdKey: Option[String]
                       )(implicit bulkOptions: BulkOptions): BulkDocument = {

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats
    val jsonMap                          = parse(document, useBigDecimalForDouble = false).extract[Map[String, Any]]
    // extract id
    val id = idKey.flatMap { i =>
      jsonMap.get(i).map(_.toString)
    }

    // extract final index name
    val index = suffixDateKey
      .flatMap { s =>
        // Expecting a date field YYYY-MM-dd ...
        jsonMap.get(s).map { d =>
          val strDate = d.toString.substring(0, 10)
          val date    = LocalDate.parse(strDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
          date.format(
            suffixDatePattern.map(DateTimeFormatter.ofPattern).getOrElse(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
        }
      }
      .map(s => s"${bulkOptions.index}-$s")
      // use suffix if available otherwise only index
      .getOrElse(bulkOptions.index)

    // extract parent key
    val parent = parentIdKey.flatMap { i =>
      jsonMap.get(i).map(_.toString)
    }

    BulkDocument(index, document, id, parent)
  }

  private[this] def toBulkAction[D](toDocument: (D) => String,
                      idKey: Option[String],
                      suffixDateKey: Option[String],
                      suffixDatePattern: Option[String],
                      update: Option[Boolean],
                      delete: Option[Boolean],
                      parentIdKey: Option[String],
                      item: D)(implicit bulkOptions: BulkOptions): BulkableAction[DocumentResult] = {
    val document = toElasticDocument(toDocument(item), idKey, suffixDateKey, suffixDatePattern, parentIdKey)
    elasticDocument2BulkAction(document, update, delete)
  }

  private[this] def elasticDocument2BulkAction(document: BulkDocument,
                                 update: Option[Boolean] = None,
                                 delete: Option[Boolean] = None): BulkableAction[DocumentResult] = {
    val builder = delete match {
      case Some(d) if d => new Delete.Builder(document.body)
      case _            => update match {
        case Some(v) if v => new Update.Builder(docAsUpsert(document.body))
        case _            => new Index.Builder(document.body)
      }
    }
    document.id.foreach(builder.id)
    builder.index(document.index)
    document.parent.foreach(s => builder.setParameter(Parameters.PARENT, s))
    builder.build()

  }

}

trait CountApi {_: JestClientCompanion =>
  def countAsync(query: String, indices: Seq[String], types: Seq[String]): Future[CountResult] = {
    val count = new Count.Builder().query(query)
    for (indice <- indices) count.addIndex(indice)
    for (t      <- types) count.addType(t)
    apply().executeAsyncPromise(count.build())
  }

  def count(query: String, indices: Seq[String], types: Seq[String]): CountResult = {
    val count = new Count.Builder().query(query)
    for (indice <- indices) count.addIndex(indice)
    for (t      <- types) count.addType(t)
    apply().execute(count.build())
  }
}

trait GetApi {_: JestClientCompanion =>
  def get[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], formats: Formats): Option[U] = {
    val result = apply().execute(
      new Get.Builder(
        index.getOrElse(
          `type`.getOrElse(
            m.runtimeClass.getSimpleName.toLowerCase
          )
        ),
        id
      ).build()
    )
    if(result.isSucceeded){
      Some(serialization.read[U](result.getSourceAsString))
    }
    else{
      None
    }
  }

  def getAsync[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[Option[U]] = {
    val promise: Promise[Option[U]] = Promise()
    apply().executeAsyncPromise(
      new Get.Builder(
        index.getOrElse(
          `type`.getOrElse(
            m.runtimeClass.getSimpleName.toLowerCase
          )
        ),
        id
      ).build()
    ) onComplete{
      case Success(result) =>
        if (result.isSucceeded)
          promise.success(Some(serialization.read[U](result.getSourceAsString)))
        else
          promise.failure(new Exception(result.getErrorMessage))
      case Failure(f)      =>
        promise.failure(f)
    }
    promise.future
  }
}

trait SearchApi {_: JestClientCompanion =>
  implicit def searchResult2Entity[M: Manifest](searchResult: SearchResult)(implicit formats: Formats): List[M] = {
    searchResult.getSourceAsStringList.asScala.map(source => serialization.read[M](source)).toList
  }

  implicit def searchResul2EntitytWithInnerHits[M, I](searchResult: SearchResult, field: String)(
    implicit formats: Formats, m: Manifest[M], i: Manifest[I]): List[(M, List[I])] = {
    def innerHits(result: JsonElement) = {
      result.getAsJsonObject.get("inner_hits").getAsJsonObject.get(field).getAsJsonObject.get("hits")
        .getAsJsonObject.get("hits").getAsJsonArray.iterator()
    }
    val gson = new Gson()
    val results = searchResult.getJsonObject.get("hits").getAsJsonObject.get("hits").getAsJsonArray.iterator()
    (for(result <- results.asScala)
      yield
        (
          result match {
            case obj: JsonObject =>
              Try{
                serialization.read[M](gson.toJson(obj.get("_source")))
              } match {
                case Success(s) => s
                case Failure(f) =>
                  logger.error(obj.toString + " -> " + f.getMessage, f)
                  throw f
              }
            case _ => serialization.read[M](result.getAsString)
          },
          (for(innerHit <- innerHits(result).asScala) yield
            innerHit match {
              case obj: JsonObject =>
                Try{
                  serialization.read[I](gson.toJson(obj.get("_source")))
                } match {
                  case Success(s) => s
                  case Failure(f) =>
                    logger.error(obj.toString + " -> " + f.getMessage, f)
                    throw f
                }
              case _ => serialization.read[I](innerHit.getAsString)
            }).toList
          )
      ).toList
  }

  implicit def sqlQuery2Search(sqlQuery: String): Option[Search] = {
    import ElasticQuery._
    select(sqlQuery) match {
      case Some(elasticSelect) =>
        import elasticSelect._
        logger.info(query)
        val search = new Search.Builder(query)
        for (source <- sources) search.addIndex(source)
        Some(search.build())
      case _       => None
    }
  }

  def searchAsync(query: String, indices: Seq[String], types: Seq[String]): Future[SearchResult] = {
    val search = new Search.Builder(query)
    for (indice <- indices) search.addIndex(indice)
    for (t      <- types) search.addType(t)
    apply().executeAsyncPromise(search.build())
  }

  def searchAsync(sqlQuery: String)(implicit ec: ExecutionContext): Future[Option[SearchResult]] = {
    val promise: Promise[Option[SearchResult]] = Promise()
    val search: Option[Search] = sqlQuery
    search match {
      case Some(s) => apply().executeAsyncPromise(s) onComplete {
        case Success(r) => promise.success(Some(r))
        case Failure(f) => promise.success(None)
      }
      case _       => promise.success(None)
    }
    promise.future
  }

  def innerSearch(query: String, indices: Seq[String], types: Seq[String]): SearchResult = {
    val search = new Search.Builder(query)
    for (indice <- indices) search.addIndex(indice)
    for (t      <- types) search.addType(t)
    apply().execute(search.build())
  }

  def innerSearch(sqlQuery: String): Option[SearchResult] = {
    val search: Option[Search] = sqlQuery
    search match {
      case Some(s) => nativeSearch(s)
      case _       => None
    }
  }

  def nativeSearch(search: Search): Option[SearchResult] = {
    val result = apply().execute(search)
    if(result.isSucceeded){
      Some(result)
    }
    else{
      logger.error(result.getErrorMessage)
      None
    }
  }

  def multiSearch(sqlQueries: List[String]): Option[MultiSearchResult] = {
    val searches = sqlQueries.flatMap(sqlQuery2Search)
    if(searches.size == sqlQueries.size){
      Some(apply().execute(new MultiSearch.Builder(searches.asJava).build()))
    }
    else{
      None
    }
  }

  def search[U](query: String, indices: Seq[String], types: Seq[String])(implicit m: Manifest[U], formats: Formats): List[U] = {
    Try(innerSearch(query, indices, types).getSourceAsStringList.asScala.map(
      source => serialization.read[U](source)
    ).toList) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        List.empty
    }
  }

  def getAll[U](sqlQuery: String)(implicit m: Manifest[U], formats: Formats): List[U] = {
    innerSearch(sqlQuery) match {
      case Some(searchResult) =>
        Try(searchResult.getSourceAsStringList.asScala.map(
          source =>
            serialization.read[U](source)
        ).toList) match {
          case Success(s) => s
          case Failure(f) =>
            logger.error(f.getMessage, f)
            List.empty
        }
      case _                  => List.empty
    }
  }

  def getAllAsync[U](sqlQuery: String)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[List[U]] = {
    val promise: Promise[List[U]] = Promise()
    searchAsync(sqlQuery) onComplete {
      case Success(s) => s match {
        case Some(searchResult) =>
          promise.success(
            searchResult.getSourceAsStringList.asScala.map(
              source => serialization.read[U](source)
            ).toList
          )
        case _                  => promise.success(List.empty)
      }
      case Failure(f) =>
        promise.failure(f)
    }
    promise.future
  }

  def getAllWithInnerHits[U, I](sqlQuery: String, field: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[(U, List[I])] = {
    innerSearch(sqlQuery) match {
      case Some(searchResult) =>
        Try(searchResul2EntitytWithInnerHits[U, I](searchResult, field)) match {
          case Success(s) => s
          case Failure(f) =>
            logger.error(f.getMessage, f)
            List.empty
        }
      case _                  => List.empty
    }
  }

  def nativeGetAllWithInnerHits[U, I](search: Search, field: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[(U, List[I])] = {
    nativeSearch(search) match {
      case Some(searchResult) =>
        Try(searchResul2EntitytWithInnerHits[U, I](searchResult, field)) match {
          case Success(s) => s
          case Failure(f) =>
            logger.error(f.getMessage, f)
            List.empty
        }
      case _                  => List.empty
    }
  }

  def multiGetAll[U](sqlQueries: List[String])(implicit m: Manifest[U], formats: Formats): List[List[U]] = {
    multiSearch(sqlQueries) match {
      case Some(multiSearchResult) =>
        multiSearchResult.getResponses.asScala.map(searchResponse =>
          searchResponse.searchResult.getSourceAsStringList.asScala.map(
            source => serialization.read[U](source)
          ).toList
        ).toList
      case _                  => List.empty
    }
  }

  def multiGetAllWithInnerHits[U, I](sqlQueries: List[String], field: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[List[(U, List[I])]] = {
    val searches = sqlQueries.flatMap(sqlQuery2Search)
    if(searches.size == sqlQueries.size){
      nativeMultiGetAllWithInnerHits(searches, field)
    }
    else{
      List.empty
    }
  }

  def nativeMultiGetAllWithInnerHits[U, I](searches: List[Search], field: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[List[(U, List[I])]] = {
    val multiSearchResult = apply().execute(new MultiSearch.Builder(searches.asJava).build())
    if(multiSearchResult.isSucceeded){
      multiSearchResult.getResponses.asScala.map(searchResponse =>
        searchResul2EntitytWithInnerHits[U, I](searchResponse.searchResult, field)
      ).toList
    }
    else{
      logger.error(multiSearchResult.getErrorMessage)
      List.empty
    }
  }

}

trait JestProvider[T <: Timestamped] extends ElasticProvider[T] {_: ElasticApi[T] with ManifestWrapper[T] =>

  override protected def initIndex(): Unit = {
    Try{
      createIndex(index)
      addAlias(index, alias)
      setMapping(index, `type`, loadMapping(mappingPath))
    } match {
      case Success(_) => logger.info(s"index:$index type:${`type`} alias:$alias created")
      case Failure(f) => logger.error(s"!!!!! index:$index type:${`type`} alias:$alias -> ${f.getMessage}", f)
    }
  }

  /**
    * Creates the unerlying document to the external system
    *
    * @param document - the document to create
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def createDocument(document: T)(implicit t: ClassTag[T]): Boolean = {
    implicit val jestClient = apply()
    Try(index(document, Some(index), Some(`type`))) match {
      case Success(_) => true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        false
    }
  }

  /**
    * Updates the unerlying document to the external system
    *
    * @param document - the document to update
    * @param upsert   - whether or not to create the underlying document if it does not exist in the external system
    * @param t        - implicit ClassTag for T
    * @return whether the operation is successful or not
    */
  override def updateDocument(document: T, upsert: Boolean)(implicit t: ClassTag[T]): Boolean = {
    implicit val jestClient = apply()
    Try(update(document, Some(index), Some(`type`), upsert)) match {
      case Success(_) => true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        false
    }
  }

  /**
    * Deletes the unerlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to delete
    * @return whether the operation is successful or not
    */
  override def deleteDocument(uuid: String): Boolean = {
    implicit val jestClient = apply()
    Try(
      delete(uuid, Some(index), Some(`type`))
    ) match {
      case Success(value) => value
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        false
    }
  }

  /**
    * Upserts the unerlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to upsert
    * @param data - a map including all the properties and values tu upsert for the document
    * @return whether the operation is successful or not
    */
  override def upsertDocument(uuid: String, data: String): Boolean = {
    implicit val jestClient = apply()
    logger.debug(s"Upserting document $uuid with $data")
    Try(
      update(
        index,
        `type`,
        uuid,
        data,
        upsert = true
      )
    ) match {
      case Success(_) => true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        false
    }
  }

  /**
    * Load the document referenced by its uuid
    *
    * @param uuid - the document uuid
    * @return the document retrieved, None otherwise
    */
  override def loadDocument(uuid: String)(implicit m: Manifest[T], formats: Formats): Option[T] = {
    implicit val jestClient = apply()
    Try(get(uuid, Some(index), Some(`type`))) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        None
    }
  }

  /**
    * Search documents
    *
    * @param query - the search query
    * @return the documents founds or an empty list otherwise
    */
  override def searchDocuments(query: String)(implicit m: Manifest[T], formats: Formats): List[T] = {
    implicit val jestClient = apply()
    Try(getAll(query)) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        jestClient.close()
        List.empty
    }
  }

}