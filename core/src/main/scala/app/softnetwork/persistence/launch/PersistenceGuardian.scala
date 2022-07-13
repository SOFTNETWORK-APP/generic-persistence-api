package app.softnetwork.persistence.launch

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Join, Subscribe}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.{actor => classic}
import app.softnetwork.persistence._
import message.{Command, Event, CommandResult}
import model.State
import app.softnetwork.persistence.config.Settings._
import app.softnetwork.persistence.query.{EventProcessor, EventProcessorStream, SchemaProvider}
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.persistence.typed.Singleton

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

import scala.language.implicitConversions

/**
  * Created by smanciot on 15/05/2020.
  */
trait PersistenceGuardian extends ClusterDomainEventHandler {_: SchemaProvider =>

  /**
    * initialize all entities
    *
    */
  def entities: ActorSystem[_] => Seq[PersistentEntity[_, _, _, _]] = _ => Seq.empty

  /**
    *
    * initialize all singletons
    */
  def singletons: ActorSystem[_] => Seq[Singleton[_]] = _ => Seq.empty

  /**
    * initialize all event processor streams
    *
    */
  def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = _ => Seq.empty

  def systemVersion(): String = version

  def initSystem: ActorSystem[_] => Unit = _ => ()

  def startSystem: ActorSystem[_] => Unit = _ => ()

  def banner: String =
    """
      | ____         __ _              _                      _
      |/ ___|  ___  / _| |_ _ __   ___| |___      _____  _ __| | __
      |\___ \ / _ \| |_| __| '_ \ / _ \ __\ \ /\ / / _ \| '__| |/ /
      | ___) | (_) |  _| |_| | | |  __/ |_ \ V  V / (_) | |  |   <
      ||____/ \___/|_|  \__|_| |_|\___|\__| \_/\_/ \___/|_|  |_|\_\
      |
      |""".stripMargin

  def setup(): Behavior[ClusterDomainEvent] = {
    Behaviors.setup[ClusterDomainEvent] { context =>
      // initialize database
      initSchema()

      val system = context.system

      val cluster: Cluster = Cluster(system)

      context.log.info("Starting up cluster listener...")
      cluster.subscriptions ! Subscribe(context.self, classOf[ClusterDomainEvent])

      // initialize behaviors
      for(behavior <- entities(system)) {
        behavior.entity.init(system, behavior.role)
      }

      // initialize bootstrap
      if(AkkaClusterWithBootstrap){
        implicit val ec: ExecutionContextExecutor = system.executionContext

        // Akka Management hosts the HTTP routes used by bootstrap
        AkkaManagement(system).start().onComplete {
          case Success(url) =>
            system.log.info(s"akka management started @ $url")
            // Starting the bootstrap process needs to be done explicitly
            ClusterBootstrap(system).start()
          case Failure(f) =>
            system.log.error("akka management failed to start", f)
            throw f
        }
      }
      else if(AkkaClusterSeedNodes.isEmpty){
        // join the cluster
        val address: classic.Address = cluster.selfMember.address
        context.log.info(s"Try to join this cluster node with the node specified by [$address]")
        cluster.manager ! Join(address)
      }
      else{
        context.log.info("Self join will not be performed")
      }

      Behaviors.receiveMessagePartial {
        case event: ClusterDomainEvent =>
          context.log.info("Cluster Domain Event: {}", event)
          event match {
            case MemberUp(member) if member.address.equals(cluster.selfMember.address) =>

              // initialize singletons
              for(singleton <- singletons(system)) {
                singleton.init(context)
              }

              // initialize event streams
              for(eventProcessorStream <- eventProcessorStreams(system)) {
                context.spawnAnonymous[Nothing](EventProcessor(eventProcessorStream))
              }

              // print a cool banner ;)
              println(banner)
              println(s"V ${systemVersion()}")

              // additional system initialization
              initSystem(system)

              // start the system
              startSystem(system)

            case _ =>
          }
          handleEvent(event)(system)
          Behaviors.same
      }
    }
  }
}

object PersistenceGuardian {
  implicit def entity2PersistentEntity[C <: Command,S <: State,E <: Event,R <: CommandResult](entity: EntityBehavior[C, S, E, R]): PersistentEntity[C, S, E, R] = {
    PersistentEntity(entity, Some(""))
  }
}
