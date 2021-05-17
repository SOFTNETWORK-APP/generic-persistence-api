package app.softnetwork.counter.persistence.data

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, ActorRef}
import akka.cluster.ddata.{SelfUniqueAddress, PNCounterKey, PNCounter}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}

import org.slf4j.Logger

import app.softnetwork.counter.message._

/**
  * Created by smanciot on 27/03/2021.
  */
trait Counter {
  import Counter._
  def apply(key: PNCounterKey): Behavior[CounterCommand] =
    Behaviors.setup[CounterCommand] { context =>
      //#selfUniqueAddress
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
      //#selfUniqueAddress

      // adapter that turns the response messages from the replicator into our own protocol
      DistributedData.withReplicatorMessageAdapter[CounterCommand, PNCounter] { replicatorAdapter =>
        //#subscribe
        // Subscribe to changes of the given `key`.
        replicatorAdapter.subscribe(key, InternalSubscribeResponse.apply)
        //#subscribe

        def updated(cachedValue: Int): Behavior[CounterCommand] = {
          def handleCommand(command: CounterCommand, maybeReplyTo: Option[ActorRef[CounterResult]])(
            implicit system: ActorSystem[_], log: Logger): Behavior[CounterCommand] = {
            command match {
              case IncrementCounter =>
                replicatorAdapter.askUpdate(
                  askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(_ :+ 1),
                  InternalUpdateResponse.apply)
                Behaviors.same[CounterCommand]

              case DecrementCounter =>
                replicatorAdapter.askUpdate(
                  askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(_.decrement(1)),
                  InternalUpdateResponse.apply)
                Behaviors.same[CounterCommand]

              case cmd: ResetCounter =>
                replicatorAdapter.askUpdate(
                  askReplyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, askReplyTo)(c =>
                   c.resetDelta  :+ cmd.value
                  ),
                  InternalUpdateResponse.apply)
                Behaviors.same[CounterCommand]

              case GetCounterValue =>
                replicatorAdapter.askGet(
                  askReplyTo => Replicator.Get(key, Replicator.ReadLocal, askReplyTo),
                  value => InternalGetResponse(value, maybeReplyTo))
                Behaviors.same[CounterCommand]

              case GetCounterCachedValue =>
                maybeReplyTo.foreach(_ ! CounterLoaded(cachedValue))
                Behaviors.same[CounterCommand]

              case UnsubscribeCounter =>
                replicatorAdapter.unsubscribe(key)
                Behaviors.same[CounterCommand]

              case internal: InternalCounterCommand =>
                internal match {
                  case InternalUpdateResponse(_) => Behaviors.same[CounterCommand] // ok

                  case InternalGetResponse(rsp @ Replicator.GetSuccess(`key`), replyTo) =>
                    val value = rsp.get(key).value.toInt
                    replyTo.foreach(_ ! CounterLoaded(value))
                    Behaviors.same[CounterCommand]

                  case InternalGetResponse(_, _) =>
                    Behaviors.unhandled[CounterCommand] // not dealing with failures

                  case InternalSubscribeResponse(chg @ Replicator.Changed(`key`)) =>
                    val value = chg.get(key).value.intValue
                    updated(value)

                  case InternalSubscribeResponse(Replicator.Deleted(_)) =>
                    Behaviors.unhandled[CounterCommand] // no deletes
                }

              case _ => Behaviors.same[CounterCommand]
            }
          }

          Behaviors.receive[CounterCommand] { (context, command) =>
            command match {
              case cmd: CounterCommandWrapper =>
                handleCommand(cmd.command, Some(cmd.replyTo))(context.system, context.log)
              case cmd: CounterCommand => handleCommand(cmd, None)(context.system, context.log)
              case _ => Behaviors.same
            }
          }
        }

        updated(cachedValue = 0)
      }

    }
}

object Counter extends Counter {
  private case class InternalUpdateResponse(rsp: Replicator.UpdateResponse[PNCounter]) extends InternalCounterCommand
  private case class InternalGetResponse(rsp: Replicator.GetResponse[PNCounter], replyTo: Option[ActorRef[CounterResult]])
    extends InternalCounterCommand
  private case class InternalSubscribeResponse(chg: Replicator.SubscribeResponse[PNCounter]) extends InternalCounterCommand
}