package app.softnetwork.persistence.person

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.generateUUID
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.person.message._
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  InMemoryPersonPersistenceProvider,
  PersonToExternalProcessorStream
}
import app.softnetwork.persistence.person.typed.PersonBehavior
import app.softnetwork.persistence.query.{EventProcessorStream, ExternalPersistenceProvider}
import app.softnetwork.persistence.scalatest.PersistenceTestKit
import app.softnetwork.persistence.schema.SchemaProvider
import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import org.scalatest.wordspec.AnyWordSpecLike

trait PersonTestKit extends PersonHandler with AnyWordSpecLike with PersistenceTestKit {
  _: SchemaProvider =>

  implicit def personSystem: ActorSystem[_] = typedSystem()

  def externalPersistenceProvider: ExternalPersistenceProvider[Person] =
    InMemoryPersonPersistenceProvider

  def person2ExternalProcessorStream: ActorSystem[_] => PersonToExternalProcessorStream

  import PersistenceGuardian._

  /** initialize all entities
    */
  override def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = _ =>
    Seq(PersonBehavior)

  /** initialize all event processor streams
    */
  override def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = sys =>
    Seq(
      person2ExternalProcessorStream(sys)
    )

  val uuid: String = generateUUID()

  val birthday = "26/12/1972"

  "handler" should {

    "add person" in {
      val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
      subscribeProbe(probe)
      ?(uuid, AddPerson("name", birthday)) await {
        case PersonAdded =>
          assert(loadPerson(uuid).map(_.name).getOrElse("") == "name")
          probe.receiveMessage() match {
            case _: PersonCreatedEvent =>
              implicit def formats: Formats = commonFormats
              assert(
                externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name"
              )
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
    "not load person that does not exist" in {
      ?("fake", LoadPerson) await {
        case PersonNotFound =>
        case other          => fail(other.toString)
      }
    }
    "update person" in {
      val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
      subscribeProbe(probe)
      ?(uuid, UpdatePerson("name2", birthday)) await {
        case PersonUpdated =>
          assert(loadPerson(uuid).map(_.name).getOrElse("") == "name2")
          probe.receiveMessage() match {
            case _: PersonUpdatedEvent =>
              implicit def formats: Formats = commonFormats
              assert(
                externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name2"
              )
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
    "not update person that does not exist" in {
      ?("fake", UpdatePerson("fake", birthday)) await {
        case PersonNotFound =>
        case other          => fail(other.toString)
      }
    }
    "update person name" in {
      val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
      subscribeProbe(probe)
      ?(uuid, UpdateName("name3")) await {
        case NameUpdated =>
          assert(loadPerson(uuid).map(_.name).getOrElse("") == "name3")
          probe.receiveMessage() match {
            case _: NameUpdatedEvent =>
              implicit def formats: Formats = commonFormats
              assert(
                externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name3"
              )
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
    "not update person name if person does not exist" in {
      ?("fake", UpdateName("fake")) await {
        case PersonNotFound =>
        case other          => fail(other.toString)
      }
    }
    "delete person" in {
      val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
      subscribeProbe(probe)
      ?(uuid, DeletePerson) await {
        case PersonDeleted =>
          assert(loadPerson(uuid).isEmpty)
          probe.receiveMessage() match {
            case _: PersonDeletedEvent =>
              implicit def formats: Formats = commonFormats
              assert(externalPersistenceProvider.loadDocument(uuid).isEmpty)
            case other => fail(other.toString)
          }
        case other => fail(other.toString)
      }
    }
    "not delete person that does not exist" in {
      ?("fake", DeletePerson) await {
        case PersonNotFound =>
        case other          => fail(other.toString)
      }
    }
  }

  def loadPerson(uuid: String): Option[Person] = {
    ?(uuid, LoadPerson) await {
      case PersonLoaded(person) => Some(person)
      case _                    => None
    }
  }
}
