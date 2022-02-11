package app.softnetwork.session.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef

import akka.persistence.typed.scaladsl.Effect

import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData}

import app.softnetwork.persistence.model.State

import app.softnetwork.persistence.typed._
import app.softnetwork.session.model.SessionData

import app.softnetwork.session.message._

import org.softnetwork.session.model.Session

import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * Created by smanciot on 14/04/2020.
  */
object RefreshToken {
  @SerialVersionUID(0L)
  case class RefreshTokenState[T <: SessionData](data: RefreshTokenData[T]) extends State {
    val uuid = data.selector
  }
}

import RefreshToken._

trait RefreshTokenBehavior[T <: SessionData]
  extends EntityBehavior[RefreshTokenCommand, RefreshTokenState[T], RefreshTokenEvent, RefreshTokenResult] {

  /**
    *
    * @param entityId - entity identity
    * @param state   - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand( entityId: String,
                              state: Option[RefreshTokenState[T]],
                              command: RefreshTokenCommand,
                              replyTo: Option[ActorRef[RefreshTokenResult]],
                              timers: TimerScheduler[RefreshTokenCommand])(
    implicit context: ActorContext[RefreshTokenCommand]): Effect[RefreshTokenEvent, Option[RefreshTokenState[T]]] = {
    command match {

      case cmd: StoreRefreshToken[T] =>
        Effect.persist[RefreshTokenEvent, Option[RefreshTokenState[T]]](RefreshTokenStored(cmd.data))
          .thenRun(_ => RefreshTokenStored(cmd.data) ~> replyTo)

      case cmd: RemoveRefreshToken   =>
        Effect.persist[RefreshTokenEvent, Option[RefreshTokenState[T]]](RefreshTokenRemoved(cmd.selector))
          .thenRun(_ => RefreshTokenRemoved(cmd.selector) ~> replyTo).thenStop()

      case cmd: LookupRefreshToken   =>
        Effect.none.thenRun(_ =>           
          LookupRefreshTokenResult(
            state.map((s) => RefreshTokenLookupResult(
              s.data.tokenHash,
              s.data.expires,
              () => s.data.forSession
            ))
          ) ~> replyTo
        )

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[RefreshTokenState[T]], event: RefreshTokenEvent)(
    implicit context: ActorContext[_]): Option[RefreshTokenState[T]] = {
    event match {
      case e: RefreshTokenStored[T] => Some(RefreshTokenState(e.data))
      case _: RefreshTokenRemoved   => emptyState
      case _                        => super.handleEvent(state, event)
    }
  }

}

trait SessionRefreshTokenBehavior extends RefreshTokenBehavior[Session]{
  override val persistenceId = "Session"

}

object SessionRefreshTokenBehavior extends SessionRefreshTokenBehavior
