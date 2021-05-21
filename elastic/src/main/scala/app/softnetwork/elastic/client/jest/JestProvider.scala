package app.softnetwork.elastic.client.jest

import app.softnetwork.elastic.client._
import app.softnetwork.elastic.persistence.query.ElasticProvider
import app.softnetwork.elastic.sql.{ElasticQuery, SQLQuery}
import app.softnetwork.persistence.ManifestWrapper
import app.softnetwork.persistence.model.Timestamped
import app.softnetwork.serialization._
import io.searchbox.action.BulkableAction
import io.searchbox.core._
import org.json4s.Formats

import scala.collection.JavaConverters._

/**
  * Created by smanciot on 20/05/2021.
  */
trait JestProvider[T <: Timestamped] extends ElasticProvider[T] with JestClientApi {_: ManifestWrapper[T] =>
}

object JestProvider {
  implicit class SearchSQLQuery(sqlQuery: SQLQuery){
    def search: Option[Search] = {
      import ElasticQuery._
      select(sqlQuery) match {
        case Some(elasticSelect) =>
          import elasticSelect._
          Console.println(query)
          val search = new Search.Builder(query)
          for (source <- sources) search.addIndex(source)
          Some(search.build())
        case _       => None
      }
    }
  }

  implicit class SearchJSONQuery(jsonQuery: JSONQuery){
    def search: Search = {
      import jsonQuery._
      val _search = new Search.Builder(query)
      for (indice <- indices) _search.addIndex(indice)
      for (t      <- types) _search.addType(t)
      _search.build()
    }
  }

  implicit class SearchResults(searchResult: SearchResult) {
    def apply[M: Manifest]()(implicit formats: Formats): List[M] = {
      searchResult.getSourceAsStringList.asScala.map(source => serialization.read[M](source)).toList
    }
  }

  implicit class JestBulkAction(bulkableAction: BulkableAction[DocumentResult]) {
    def index: String = bulkableAction.getIndex
  }
}
