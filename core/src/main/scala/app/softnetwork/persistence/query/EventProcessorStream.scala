package app.softnetwork.persistence.query

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.{Done, NotUsed}
import akka.persistence.query.Offset
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{KillSwitches, Materializer, RestartSettings, SharedKillSwitch}
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.persistence.message.Event
import app.softnetwork.persistence.typed._
import akka.{actor => classic}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

/**
  * Created by smanciot on 16/05/2020.
  */
object EventProcessor {

  def apply(eventProcessorStream: EventProcessorStream[_]): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      val killSwitch = KillSwitches.shared("eventProcessorSwitch")
      eventProcessorStream.runQueryStream(killSwitch)
      Behaviors.receiveSignal[Nothing] {
        case (_, PostStop) =>
          ctx.log.info(s"Stopping stream ${eventProcessorStream.id}")
          killSwitch.shutdown()
          Behaviors.same
      }
    }
  }

}

trait EventStream {

  def tag: String

  def eventProcessorId: String = tag

}



trait EventProcessorStream[E <: Event] extends EventStream with StrictLogging { _: JournalProvider =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContextExecutor = system.executionContext

  override implicit lazy val classicSystem: classic.ActorSystem = system

  implicit def mat: Materializer = Materializer(classicSystem)

  protected def init(): Unit = {}

  final lazy val id = platformEventProcessorId

  /**
    *
    * Processing event
    *
    * @param event - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr - sequence number
    * @return
    */
  protected def processEvent(event: E, persistenceId: PersistenceId, sequenceNr: Long): Future[Done]

  private[this] def processEventsByTag(offset: Offset): Source[Offset, NotUsed] = {
    eventsByTag(platformTag, offset).mapAsync(1) { eventEnvelope =>
      eventEnvelope.event match {
        case event: E =>
          processEvent(
            event,
            PersistenceId.ofUniqueId(eventEnvelope.persistenceId),
            eventEnvelope.sequenceNr
          ).map(_ => eventEnvelope.offset)
        case other =>
          logger.error("Unexpected event [{}]", other)
          Future.failed(new IllegalArgumentException(s"Unexpected event [${other.getClass.getName}]"))
      }
    }
  }

  final def runQueryStream(killSwitch: SharedKillSwitch): Unit = {
    init()
    RestartSource
      .withBackoff(RestartSettings(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1)) { () =>
        Source.futureSource {
          initJournalProvider()
          readOffset().map { offset =>
            logger.info("Starting stream {} for tag [{}] from offset [{}]", platformEventProcessorId, platformTag, offset)
            processEventsByTag(offset)
              // groupedWithin can be used here to improve performance by reducing number of offset writes,
              // with the trade-off of possibility of more duplicate events when stream is restarted
              .mapAsync(1)(writeOffset)
          }
        }
      }
      .via(killSwitch.flow)
      .runWith(Sink.ignore)
  }

}
