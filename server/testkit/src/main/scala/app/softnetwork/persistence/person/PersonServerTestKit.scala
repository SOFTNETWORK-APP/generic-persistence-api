package app.softnetwork.persistence.person

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.PersistenceScalatestRouteTest
import app.softnetwork.api.server.{ApiRoute, ApiRoutes}
import app.softnetwork.api.server.config.ServerSettings.RootPath
import app.softnetwork.persistence.launch.{PersistenceGuardian, PersistentEntity}
import app.softnetwork.persistence.person.message._
import app.softnetwork.persistence.person.model.Person
import app.softnetwork.persistence.person.query.{
  InMemoryPersonPersistenceProvider,
  PersonToExternalProcessorStream
}
import app.softnetwork.persistence.person.typed.PersonBehavior
import app.softnetwork.persistence.query.{EventProcessorStream, ExternalPersistenceProvider}
import app.softnetwork.persistence.schema.Schema
//import app.softnetwork.serialization.commonFormats
import org.json4s.Formats
import org.scalatest.wordspec.AnyWordSpecLike

trait PersonServerTestKit
    extends AnyWordSpecLike
    with PersistenceScalatestRouteTest
    with ApiRoutes {
  _: Schema =>

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

  override def apiRoutes: ActorSystem[_] => List[ApiRoute] =
    system =>
      List(
        PersonService(system)
      )

  val birthday = "26/12/1972"

  var uuid: String = _

  import app.softnetwork.serialization._

  "add person" in {
    val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
    subscribeProbe(probe)
    Post(s"/$RootPath/person", AddPerson("name", birthday)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      uuid = responseAs[PersonAdded].uuid
      assert(loadPerson(uuid).map(_.name).getOrElse("") == "name")
      probe.receiveMessage() match {
        case _: PersonCreatedEvent =>
          implicit def formats: Formats = commonFormats

          assert(
            externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name"
          )
        case other => fail(other.toString)
      }
    }
  }
  "not load person that does not exist" in {
    assert(loadPerson("fake").isEmpty)
  }
  "update person" in {
    val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
    subscribeProbe(probe)
    Post(s"/$RootPath/person/$uuid", UpdatePerson("name2", birthday)) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      assert(loadPerson(uuid).map(_.name).getOrElse("") == "name2")
      probe.receiveMessage() match {
        case _: PersonUpdatedEvent =>
          implicit def formats: Formats = commonFormats

          assert(
            externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name2"
          )
        case other => fail(other.toString)
      }
    }
  }
  "not update person that does not exist" in {
    Post(s"/$RootPath/person/fake", UpdatePerson("fake", birthday)) ~> routes ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }
  "update person name" in {
    val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
    subscribeProbe(probe)
    Post(s"/$RootPath/person/name/$uuid", UpdateName("name3")) ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      assert(loadPerson(uuid).map(_.name).getOrElse("") == "name3")
      probe.receiveMessage() match {
        case _: NameUpdatedEvent =>
          implicit def formats: Formats = commonFormats

          assert(
            externalPersistenceProvider.loadDocument(uuid).map(_.name).getOrElse("") == "name3"
          )
        case other => fail(other.toString)
      }
    }
  }
  "not update person name if person does not exist" in {
    Post(s"/$RootPath/person/name/fake", UpdateName("fake")) ~> routes ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }
  "delete person" in {
    val probe: TestProbe[PersonEvent] = createTestProbe[PersonEvent]()
    subscribeProbe(probe)
    Delete(s"/$RootPath/person/$uuid") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      assert(loadPerson(uuid).isEmpty)
      probe.receiveMessage() match {
        case _: PersonDeletedEvent =>
          implicit def formats: Formats = commonFormats

          assert(externalPersistenceProvider.loadDocument(uuid).isEmpty)
        case other => fail(other.toString)
      }
    }
  }
  "not delete person that does not exist" in {
    Delete(s"/$RootPath/person/fake") ~> routes ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  def loadPerson(uuid: String): Option[Person] = {
    Get(s"/$RootPath/person/$uuid") ~> routes ~> check {
      if (status == StatusCodes.OK) {
        Some(responseAs[PersonLoaded].person)
      } else {
        None
      }
    }
  }
}
