package app.softnetwork.session

import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData}
import app.softnetwork.persistence.message.{Event, CommandResult, EntityCommand}

/**
  * Created by smanciot on 16/05/2020.
  */
package object message {

  /** Commands **/

  sealed trait RefreshTokenCommand extends EntityCommand {
    def selector: String
    override val id: String = selector
  }

  @SerialVersionUID(0L)
  case class LookupRefreshToken(selector: String) extends RefreshTokenCommand

  @SerialVersionUID(0L)
  case class StoreRefreshToken[T](data: RefreshTokenData[T]) extends RefreshTokenCommand {
    override val selector: String = data.selector
  }

  @SerialVersionUID(0L)
  case class RemoveRefreshToken(selector: String) extends RefreshTokenCommand

  /** Commands Result **/

  sealed trait RefreshTokenResult extends CommandResult

  @SerialVersionUID(0L)
  case class LookupRefreshTokenResult[T](data: Option[RefreshTokenLookupResult[T]]) extends RefreshTokenResult

  sealed trait RefreshTokenEvent extends Event

  @SerialVersionUID(0L)
  case class RefreshTokenStored[T](data: RefreshTokenData[T]) extends RefreshTokenEvent with RefreshTokenResult

  @SerialVersionUID(0L)
  case class RefreshTokenRemoved(selector: String) extends RefreshTokenEvent with RefreshTokenResult}
