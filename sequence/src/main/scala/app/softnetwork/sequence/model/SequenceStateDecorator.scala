package app.softnetwork.sequence.model

/**
  * Created by smanciot on 26/05/2020.
  */
trait SequenceStateDecorator {_: SequenceState =>
  val uuid: String = name
}
