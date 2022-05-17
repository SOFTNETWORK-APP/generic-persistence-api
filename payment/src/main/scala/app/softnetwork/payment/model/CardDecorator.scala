package app.softnetwork.payment.model

import java.text.SimpleDateFormat
import java.util.Date

import scala.util.{Failure, Success, Try}

trait CardDecorator{_: Card =>
  lazy val expired: Boolean = {
    val sdf = new SimpleDateFormat("MMyy")
    Try(sdf.parse(expirationDate)) match {
      case Success(date) =>
        sdf.parse(sdf.format(new Date())).after(date)
      case Failure(f)    =>
        false
    }
  }

  lazy val owner: CardOwner =
    CardOwner.defaultInstance
      .withFirstName(firstName)
      .withLastName(lastName)
      .withBirthday(birthday)
}
