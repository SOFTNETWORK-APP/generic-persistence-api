package app.softnetwork.elastic.client.jest

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import app.softnetwork.elastic.client._
import app.softnetwork.elastic.sql.{ElasticQuery, SQLQueries, SQLQuery}
import app.softnetwork.persistence.message.CountResponse
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.serialization._
import io.searchbox.action.BulkableAction
import io.searchbox.core._
import io.searchbox.core.search.aggregation.RootAggregation
import io.searchbox.indices.aliases.{AddAliasMapping, ModifyAliases}
import io.searchbox.indices.mapping.PutMapping
import io.searchbox.indices.settings.UpdateSettings
import io.searchbox.indices._
import io.searchbox.params.Parameters
import org.json4s.Formats

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{Promise, Future, ExecutionContext}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 20/05/2021.
  */
trait JestClientApi extends ElasticClientApi
  with JestIndicesApi
  with JestAliasApi
  with JestUpdateSettingsApi
  with JestMappingApi
  with JestRefreshApi
  with JestFlushApi
  with JestCountApi
  with JestIndexApi
  with JestUpdateApi
  with JestDeleteApi
  with JestGetApi
  with JestSearchApi
  with JestBulkApi

trait JestIndicesApi extends IndicesApi with JestClientCompanion {
  override def createIndex(index: String, settings: String = defaultSettings) =
    apply().execute(new CreateIndex.Builder(index).settings(settings).build()).isSucceeded
  override def deleteIndex(index: String) = apply().execute(new DeleteIndex.Builder(index).build()).isSucceeded
  override def closeIndex(index: String) = apply().execute(new CloseIndex.Builder(index).build()).isSucceeded
  override def openIndex(index: String) = apply().execute(new OpenIndex.Builder(index).build()).isSucceeded
}

trait JestAliasApi extends AliasApi with JestClientCompanion {
  override def addAlias(index: String, alias: String) = {
    apply().execute(
      new ModifyAliases.Builder(
        new AddAliasMapping.Builder(index, alias).build()
      ).build()
    ).isSucceeded
  }
}

trait JestUpdateSettingsApi extends UpdateSettingsApi with JestClientCompanion {_: IndicesApi =>
  override def updateSettings(index: String, settings: String = defaultSettings) =
    closeIndex(index) &&
      apply().execute(new UpdateSettings.Builder(settings).addIndex(index).build()).isSucceeded &&
      openIndex(index)
}

trait JestMappingApi extends MappingApi with JestClientCompanion {
  override def setMapping(index: String, `type`: String, mapping: String) =
    apply().execute(new PutMapping.Builder(index, `type`, mapping).build()).isSucceeded
}

trait JestRefreshApi extends RefreshApi with JestClientCompanion {
  override def refresh(index: String) = apply().execute(new Refresh.Builder().addIndex(index).build()).isSucceeded
}

trait JestFlushApi extends FlushApi with JestClientCompanion {
  override def flush(index: String, force: Boolean = true, wait: Boolean = true) = apply().execute(
    new Flush.Builder().addIndex(index).force(force).waitIfOngoing(wait).build()
  ).isSucceeded
}

trait JestCountApi extends CountApi with JestClientCompanion {
  override def countAsync(jsonQuery: JSONQuery)(implicit ec: ExecutionContext
  ): Future[Option[Double]] = {
    import JestClientResultHandler._
    import jsonQuery._
    val count = new Count.Builder().query(query)
    for (indice <- indices) count.addIndex(indice)
    for (t      <- types) count.addType(t)
    val promise = Promise[Option[Double]]()
    apply().executeAsyncPromise(count.build()) onComplete {
      case Success(result) =>
        if(!result.isSucceeded)
          logger.error(result.getErrorMessage)
        promise.success(Option(result.getCount))
      case Failure(f) =>
        logger.error(f.getMessage, f)
        promise.failure(f)
    }
    promise.future
  }

  override def count(jsonQuery: JSONQuery): Option[Double] = {
    import jsonQuery._
    val count = new Count.Builder().query(query)
    for (indice <- indices) count.addIndex(indice)
    for (t      <- types) count.addType(t)
    val result = apply().execute(count.build())
    if(!result.isSucceeded)
      logger.error(result.getErrorMessage)
    Option(result.getCount)
  }

  override def countAsync(sqlQuery: SQLQuery)(implicit ec: ExecutionContext
  ): Future[_root_.scala.collection.Seq[CountResponse]] = {
    val futures = for (elasticCount <- ElasticQuery.count(sqlQuery)) yield {
      val promise: Promise[CountResponse] = Promise()
      import collection.immutable.Seq
      val _field = elasticCount.field
      val _sourceField = elasticCount.sourceField
      val _agg = elasticCount.agg
      val _query = elasticCount.query
      val _sources = elasticCount.sources
      _sourceField match {
        case "_id" =>
          countAsync(
            JSONQuery(_query, Seq(_sources: _*), Seq.empty[String])
          ).onComplete {
            case Success(result) => promise.success(new CountResponse(_field, result.getOrElse(0D).toInt, None))
            case Failure(f) =>
              logger.error(f.getMessage, f.fillInStackTrace())
              promise.success(new CountResponse(_field, 0, Some(f.getMessage)))
          }
        case _ =>
          import JestProvider._
          import JestClientResultHandler._
          apply().executeAsyncPromise(JSONQuery(_query, Seq(_sources: _*), Seq.empty[String]).search).onComplete {
            case Success(result) =>
              val agg = _agg.split("\\.").last

              val itAgg = _agg.split("\\.").iterator

              var root =
                if (elasticCount.nested)
                  result.getAggregations.getAggregation(itAgg.next(), classOf[RootAggregation])
                else
                  result.getAggregations

              if (elasticCount.filtered) {
                root = root.getAggregation(itAgg.next(), classOf[RootAggregation])
              }

              promise.success(
                new CountResponse(
                  _field,
                  if (elasticCount.distinct)
                    root.getCardinalityAggregation(agg).getCardinality.toInt
                  else
                    root.getValueCountAggregation(agg).getValueCount.toInt,
                  None
                )
              )

            case Failure(f) =>
              logger.error(f.getMessage, f.fillInStackTrace())
              promise.success(new CountResponse(_field, 0, Some(f.getMessage)))
          }
      }
      promise.future
    }
    Future.sequence(futures.toSeq)
  }
}

trait JestIndexApi extends IndexApi with JestClientCompanion {
  override def index(index: String, `type`: String, id: String, source: String): Boolean = {
    Try(apply().execute(
      new Index.Builder(source).index(index).`type`(`type`).id(id).build()
    )) match {
      case Success(s) =>
        if(!s.isSucceeded)
          logger.error(s.getErrorMessage)
        s.isSucceeded
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  override def indexAsync( index: String, `type`: String, id: String, source: String)(
    implicit ec: ExecutionContext): Future[Boolean] = {
    import JestClientResultHandler._
    val promise: Promise[Boolean] = Promise()
    apply().executeAsyncPromise(
      new Index.Builder(source).index(index).`type`(`type`).id(id).build()
    ) onComplete {
      case Success(s) => promise.success(s.isSucceeded)
      case Failure(f) =>
        logger.error(f.getMessage, f)
        promise.failure(f)
    }
    promise.future
  }

}

trait JestUpdateApi extends UpdateApi with JestClientCompanion {
  override def update(index: String, `type`: String, id: String, source: String, upsert: Boolean): Boolean = {
    Try(apply().execute(
      new Update.Builder(
        if(upsert)
          docAsUpsert(source)
        else
          source
      ).index(index).`type`(`type`).id(id).build()
    )) match {
      case Success(s) =>
        if(!s.isSucceeded)
          logger.error(s.getErrorMessage)
        s.isSucceeded
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }

  override def updateAsync(index: String, `type`: String, id: String, source: String, upsert: Boolean)(
    implicit ec: ExecutionContext): Future[Boolean] = {
    import JestClientResultHandler._
    val promise: Promise[Boolean] = Promise()
    apply().executeAsyncPromise(
      new Update.Builder(
        if(upsert)
          docAsUpsert(source)
        else
          source
      ).index(index).`type`(`type`).id(id).build()
    ) onComplete {
      case Success(s) =>
        if(!s.isSucceeded)
          logger.error(s.getErrorMessage)
        promise.success(s.isSucceeded)
      case Failure(f) =>
        logger.error(f.getMessage, f)
        promise.failure(f)
    }
    promise.future
  }

}

trait JestDeleteApi extends DeleteApi with JestClientCompanion {
  override def delete(uuid: String, index: String, `type`: String): Boolean = {
    val result = apply().execute(
      new Delete.Builder(uuid).index(index).`type`(`type`).build()
    )
    if(!result.isSucceeded){
      logger.error(result.getErrorMessage)
    }
    result.isSucceeded
  }

  override def deleteAsync(uuid: String, index: String, `type`: String)(
    implicit ec: ExecutionContext): Future[Boolean] = {
    import JestClientResultHandler._
    val promise: Promise[Boolean] = Promise()
    apply().executeAsyncPromise(
      new Delete.Builder(uuid).index(index).`type`(`type`).build()
    ) onComplete {
      case Success(s) =>
        if(!s.isSucceeded)
          logger.error(s.getErrorMessage)
        promise.success(s.isSucceeded)
      case Failure(f) =>
        logger.error(f.getMessage, f)
        promise.failure(f)
    }
    promise.future
  }

}

trait JestGetApi extends GetApi with JestClientCompanion {

  // GetApi
  override def get[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
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
      logger.error(result.getErrorMessage)
      None
    }
  }

  override def getAsync[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[Option[U]] = {
    import JestClientResultHandler._
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
        else{
          logger.error(result.getErrorMessage)
          promise.success(None)
        }
      case Failure(f)      =>
        logger.error(f.getMessage, f)
        promise.failure(f)
    }
    promise.future
  }

}

trait JestSearchApi extends SearchApi with JestClientCompanion {

  import JestProvider._

  override def search[U](jsonQuery: JSONQuery, indices: Seq[String], types: Seq[String])(
    implicit m: Manifest[U], formats: Formats): List[U] = {
    val search = new Search.Builder(jsonQuery.query)
    for (indice <- indices) search.addIndex(indice)
    for (t      <- types) search.addType(t)
    Try(apply().execute(search.build()).getSourceAsStringList.asScala.map(
      source => serialization.read[U](source)
    ).toList) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage, f)
        List.empty
    }
  }

  override def search[U](sqlQuery: SQLQuery)(implicit m: Manifest[U], formats: Formats): List[U] = {
    val search: Option[Search] = sqlQuery.search
    (search match {
      case Some(s) =>
        val result = apply().execute(s)
        if(result.isSucceeded){
          Some(result)
        }
        else{
          logger.error(result.getErrorMessage)
          None
        }
      case _       => None
    }) match {
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

  override def searchAsync[U](sqlQuery: SQLQuery)(implicit m: Manifest[U], ec: ExecutionContext, formats: Formats
  ): Future[List[U]] = {
    val promise = Promise[List[U]]()
    val search: Option[Search] = sqlQuery.search
    search match {
      case Some(s) =>
        import JestClientResultHandler._
        apply().executeAsyncPromise(s) onComplete {
          case Success(searchResult) =>
            promise.success(
              searchResult.getSourceAsStringList.asScala.map(
                source => serialization.read[U](source)
              ).toList
            )
          case Failure(f) =>
            promise.failure(f)
        }
      case _ => promise.success(List.empty)
    }
    promise.future
  }

  override def searchWithInnerHits[U, I](sqlQuery: SQLQuery, innerField: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[(U, List[I])] = {
    val search: Option[Search] = sqlQuery.search
    (search match {
      case Some(s) =>
        val result = apply().execute(s)
        if(result.isSucceeded){
          Some(result)
        }
        else{
          logger.error(result.getErrorMessage)
          None
        }
      case _       => None
    }) match {
      case Some(searchResult) =>
        Try(searchResult.getJsonObject ~>[U, I] innerField) match {
          case Success(s) => s
          case Failure(f) =>
            logger.error(f.getMessage, f)
            List.empty
        }
      case _                  => List.empty
    }
  }

  override def searchWithInnerHits[U, I](jsonQuery: JSONQuery, innerField: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[(U, List[I])] = {
    val result = apply().execute(jsonQuery.search)
    (if(result.isSucceeded){
      Some(result)
    }
    else{
      logger.error(result.getErrorMessage)
      None
    }) match {
      case Some(searchResult) =>
        Try(searchResult.getJsonObject ~>[U, I] innerField) match {
          case Success(s) => s
          case Failure(f) =>
            logger.error(f.getMessage, f)
            List.empty
        }
      case _                  => List.empty
    }
  }

  override def multiSearch[U](sqlQueries: SQLQueries)(implicit m: Manifest[U], formats: Formats): List[List[U]] = {
    val searches: List[Search] = sqlQueries.queries.flatMap(_.search)
    (if(searches.size == sqlQueries.queries.size){
      Some(apply().execute(new MultiSearch.Builder(searches.asJava).build()))
    }
    else{
      None
    }) match {
      case Some(multiSearchResult) =>
        multiSearchResult.getResponses.asScala.map(searchResponse =>
          searchResponse.searchResult.getSourceAsStringList.asScala.map(
            source => serialization.read[U](source)
          ).toList
        ).toList
      case _                  => List.empty
    }
  }

  override def multiSearch[U](jsonQueries: JSONQueries)(implicit m: Manifest[U], formats: Formats): List[List[U]] = {
    val searches: List[Search] = jsonQueries.queries.map(_.search)
    val multiSearchResult = apply().execute(new MultiSearch.Builder(searches.asJava).build())
    multiSearchResult.getResponses.asScala.map(searchResponse =>
      searchResponse.searchResult.getSourceAsStringList.asScala.map(
        source => serialization.read[U](source)
      ).toList
    ).toList
  }

  override def multiSearchWithInnerHits[U, I](sqlQueries: SQLQueries, innerField: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[List[(U, List[I])]] = {
    val searches: List[Search] = sqlQueries.queries.flatMap(_.search)
    if(searches.size == sqlQueries.queries.size){
      nativeMultiSearchWithInnerHits(searches, innerField)
    }
    else{
      List.empty
    }
  }

  override def multiSearchWithInnerHits[U, I](jsonQueries: JSONQueries, innerField: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[List[(U, List[I])]] = {
    nativeMultiSearchWithInnerHits(jsonQueries.queries.map(_.search), innerField)
  }

  private[this] def nativeMultiSearchWithInnerHits[U, I](searches: List[Search], innerField: String)(
    implicit m1: Manifest[U], m2: Manifest[I], formats: Formats): List[List[(U, List[I])]] = {
    val multiSearchResult = apply().execute(new MultiSearch.Builder(searches.asJava).build())
    if(multiSearchResult.isSucceeded){
      multiSearchResult.getResponses.asScala.map(searchResponse =>
        searchResponse.searchResult.getJsonObject ~>[U, I] innerField
      ).toList
    }
    else{
      logger.error(multiSearchResult.getErrorMessage)
      List.empty
    }
  }

}

trait JestBulkApi extends JestRefreshApi with JestUpdateSettingsApi with JestIndicesApi with BulkApi with JestClientCompanion {
  override type A = BulkableAction[DocumentResult]
  override type R = BulkResult

  override implicit def toBulkElasticAction(a: A) : BulkElasticAction =
    new BulkElasticAction {
      override def index: String = a.getIndex
    }

  private[this] def toBulkElasticResultItem(i: BulkResult#BulkResultItem): BulkElasticResultItem =
    new BulkElasticResultItem{
      override def index: String = i.index
    }

  override implicit def toBulkElasticResult(r: R): BulkElasticResult =
    new BulkElasticResult {
      override def items: List[BulkElasticResultItem] = r.getItems.asScala.toList.map(toBulkElasticResultItem)
    }

  override def bulk(implicit bulkOptions: BulkOptions, system: ActorSystem): Flow[Seq[A], R, NotUsed] = {
    import JestClientResultHandler._
    val parallelism = Math.max(1, bulkOptions.balance)

    Flow[Seq[BulkableAction[DocumentResult]]]
      .named("bulk")
      .mapAsyncUnordered[BulkResult](parallelism)(items => {
      logger.info(s"Starting to write batch of ${items.size}...")
      val init = new Bulk.Builder().defaultIndex(bulkOptions.index).defaultType(bulkOptions.documentType)
      val bulkQuery = items.foldLeft(init) { (current, query) =>
        current.addAction(query)
      }
      apply().executeAsyncPromise(bulkQuery.build())
    })
  }

  override def bulkResult: Flow[R, Set[String], NotUsed] =
    Flow[BulkResult]
      .named("result")
      .map(result => {
        val items   = result.getItems
        val indices = items.asScala.map(_.index).toSet
        logger.info(s"Finished to write batch of ${items.size} within ${indices.mkString(",")}.")
        indices
      })

  override def toBulkAction(bulkItem: BulkItem): A = {
    val builder = bulkItem.action match {
      case BulkAction.DELETE => new Delete.Builder(bulkItem.body)
      case BulkAction.UPDATE => new Update.Builder(bulkItem.body)
      case _ => new Index.Builder(bulkItem.body)
    }
    bulkItem.id.foreach(builder.id)
    builder.index(bulkItem.index)
    bulkItem.parent.foreach(s => builder.setParameter(Parameters.PARENT, s))
    builder.build()
  }

}