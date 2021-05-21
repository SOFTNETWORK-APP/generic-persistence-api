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
