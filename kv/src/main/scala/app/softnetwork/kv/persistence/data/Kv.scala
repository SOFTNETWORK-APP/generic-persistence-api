package app.softnetwork.kv.persistence.data

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import app.softnetwork.kv.message.{KvCommandResult, KvFound, KvNotFound}
import app.softnetwork.persistence.message.{Command, CommandWrapper}

trait Kv {
  import Kv._
  def apply(namespace: String): Behavior[KvCommand] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[KvCommand, LWWMap[String, String]] { replicator =>
      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      def dataKey(entryKey: String): LWWMapKey[String, String] =
        LWWMapKey[String, String](s"$namespace-" + math.abs(entryKey.hashCode % 100))

      def handleCommand(command: KvCommand, maybeReplyTo: Option[ActorRef[KvCommandResult]])(
        implicit system: ActorSystem[_]): Behavior[KvCommand] = {
        command match {
          case Put(key, value) =>
            replicator.askUpdate(
              askReplyTo => Update(
                dataKey(key), LWWMap.empty[String, String], WriteLocal, askReplyTo
              )(_ :+ (key -> value)),
              InternalUpdateResponse.apply
            )
            Behaviors.same

          case Remove(key) =>
            replicator.askUpdate(
              askReplyTo => Update(
                dataKey(key), LWWMap.empty[String, String], WriteLocal, askReplyTo
              )(_.remove(node, key)),
              InternalUpdateResponse.apply
            )
            Behaviors.same

          case Lookup(key) =>
            replicator.askGet(
              askReplyTo => Get(dataKey(key), ReadLocal, askReplyTo),
              rsp => InternalGetResponse(key, maybeReplyTo, rsp))
            Behaviors.same

          case InternalGetResponse(key, replyTo, g @ GetSuccess(_, _)) =>
            g.dataValue.get(key) match {
              case Some(value) => replyTo.foreach(_ ! KvFound(value))
              case _ => replyTo.foreach(_ ! KvNotFound)
            }
            Behaviors.same

          case InternalGetResponse(_, replyTo, _: NotFound[_]) =>
            replyTo.foreach(_ ! KvNotFound)
            Behaviors.same

          case InternalSubscribeResponse(Replicator.Deleted(_)) => Behaviors.same // no deletes
          case _: InternalGetResponse    => Behaviors.same // ok
          case _: InternalUpdateResponse => Behaviors.same // ok
        }
      }

      Behaviors.receive[KvCommand] { (context, command) =>
        command match {
          case w: KvCommandWrapper => handleCommand(w.command, Some(w.replyTo))(context.system)
          case c: KvCommand => handleCommand(c, None)(context.system)
          case _ => Behaviors.same
        }
      }
    }
  }
}

object Kv extends Kv {
  sealed trait KvCommand extends Command

  case class KvCommandWrapper(command: KvCommand, replyTo: ActorRef[KvCommandResult])
    extends CommandWrapper[KvCommand, KvCommandResult] with KvCommand

  trait InternalKvCommand extends KvCommand

  case class Put(key: String, value: String) extends KvCommand
  case class Remove(key: String) extends KvCommand
  case class Lookup(key: String) extends KvCommand

  private case class InternalUpdateResponse(rsp: Replicator.UpdateResponse[LWWMap[String, String]])
    extends InternalKvCommand
  private case class InternalGetResponse(key: String,
                                         replyTo: Option[ActorRef[KvCommandResult]],
                                         rsp: GetResponse[LWWMap[String, String]]) extends InternalKvCommand
  private case class InternalSubscribeResponse(chg: Replicator.SubscribeResponse[LWWMap[String, String]]) 
    extends InternalKvCommand
}