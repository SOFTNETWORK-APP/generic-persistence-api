package app.softnetwork.persistence.launch

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Join, Subscribe}
import akka.{actor => classic}
import app.softnetwork.persistence._
import app.softnetwork.persistence.config.Settings
import app.softnetwork.persistence.query.{EventProcessor, EventProcessorStream, SchemaProvider}
import app.softnetwork.persistence.typed.EntityBehavior
import app.softnetwork.persistence.typed.Singleton

/**
  * Created by smanciot on 15/05/2020.
  */
trait PersistenceGuardian extends ClusterDomainEventHandler {_: SchemaProvider =>

  /**
    * initialize all behaviors
    *
    */
  def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq.empty

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

  /**
    *
    * @return join this node to the cluster explicitly
    */
  def joinCluster(system: ActorSystem[_]) = {
    // join cluster
    val cluster = Cluster(system)
    val address: classic.Address = cluster.selfMember.address
    system.log.info(s"Try to join this cluster node with the node specified by [$address]")
    cluster.manager ! Join(address)
  }

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

      // initialize behaviors
      for(behavior <- behaviors(system)) {
        behavior.init(system)
      }

      // initialize singletons
      for(singleton <- singletons(system)) {
        singleton.init(context)
      }

      // join the cluster
      if(Settings.AkkaClusterSeedNodes.isEmpty){
        joinCluster(system)
      }
      else{
        context.log.info("Self join will not be performed")
      }

      context.log.info("Starting up cluster listener...")
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[ClusterDomainEvent])

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

      Behaviors.receiveMessagePartial {
        case event: ClusterDomainEvent =>
          context.log.info("Cluster Domain Event: {}", event)
          handleEvent(event)(system)
          Behaviors.same
      }
    }
  }
}

trait ClusterDomainEventHandler {
  def handleEvent(event: ClusterDomainEvent)(implicit system: ActorSystem[_]): Unit = ()
}
