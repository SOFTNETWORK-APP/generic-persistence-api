package app.softnetwork.elastic.persistence.typed

import akka.actor.typed.ActorSystem
import app.softnetwork.elastic.persistence.query.ElasticProvider
import app.softnetwork.persistence.scalatest.InMemoryPersistenceTestKit

import org.scalatest.wordspec.AnyWordSpecLike
import app.softnetwork.persistence.message.CommandWrapper
import app.softnetwork.elastic.client.MockElasticClientApi
import app.softnetwork.elastic.message._
import app.softnetwork.elastic.model.Sample
import app.softnetwork.persistence.launch.PersistentEntity

import scala.language.implicitConversions

/**
  * Created by smanciot on 11/04/2020.
  */
class ElasticBehaviorSpec extends AnyWordSpecLike with InMemoryPersistenceTestKit {

  import app.softnetwork.persistence.launch.PersistenceGuardian._

  /**
    * initialize all entities
    *
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = _ => List(
    SampleBehavior
  )

  import SampleBehavior._

  "ElasticTypedActor" must {

    "CreateDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "create")
      ref ! CommandWrapper(CreateDocument(Sample("create")), probe.ref)
      probe.expectMessage(DocumentCreated("create"))
    }

    "UpdateDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "update")
      ref ! CommandWrapper(UpdateDocument(Sample("update")), probe.ref)
      probe.expectMessage(DocumentUpdated("update"))
    }

    "DeleteDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "delete")
      ref ! CommandWrapper(CreateDocument(Sample("delete")), probe.ref)
      probe.expectMessage(DocumentCreated("delete"))
      ref ! CommandWrapper(DeleteDocument("delete"), probe.ref)
      probe.expectMessage(DocumentDeleted)
    }

    "LoadDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "load")
      val sample = Sample("load")
      ref ! CommandWrapper(CreateDocument(sample), probe.ref)
      probe.expectMessage(DocumentCreated("load"))
      ref ! CommandWrapper(LoadDocument("load"), probe.ref)
      probe.expectMessage(DocumentLoaded(sample))
    }

  }

}

object SampleBehavior extends ElasticBehavior[Sample] with MockElasticClientApi {

  override val persistenceId = "Sample"

  override protected val manifestWrapper: SampleBehavior.ManifestW = ManifestW()

}
