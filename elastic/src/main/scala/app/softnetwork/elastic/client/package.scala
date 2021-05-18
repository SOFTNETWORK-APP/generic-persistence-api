package app.softnetwork.elastic

import akka.stream.{Attributes, Outlet, Inlet, FlowShape}
import akka.stream.stage.{GraphStageLogic, GraphStage}

import io.searchbox.action.BulkableAction
import io.searchbox.core.DocumentResult

import app.softnetwork.persistence.model.Timestamped

import scala.collection.mutable

/**
  * Created by smanciot on 30/06/2018.
  */
package object client {

  case class ElasticCredentials(url: String, username: String, password: String)

  case class BulkDocument(index: String, body: String, id: Option[String], parent: Option[String])

  case class BulkOptions(index: String, documentType: String, maxBulkSize: Int = 100, balance: Int = 1, disableRefresh: Boolean = false)

  case class BulkSettings[T <: Timestamped](bulkApi: BulkApi, disableRefresh: Boolean = false)
    extends GraphStage[FlowShape[BulkableAction[DocumentResult], BulkableAction[DocumentResult]]] {

    val in = Inlet[BulkableAction[DocumentResult]]("Filter.in")
    val out = Outlet[BulkableAction[DocumentResult]]("Filter.out")

    val shape = FlowShape.of(in, out)

    val indices = mutable.Set.empty[String]

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        setHandler(in, () => {
          val elem = grab(in)
          val index = elem.getIndex
          if (!indices.contains(index)) {
            if (disableRefresh) {
              bulkApi.prepareBulk(index)
            }
            indices.add(index)
          }
          push(out, elem)
        })
        setHandler(out, () => {
          pull(in)
        })
      }
    }
  }

  def docAsUpsert(doc: String): String = s"""{"doc":$doc,"doc_as_upsert":true}"""
}
