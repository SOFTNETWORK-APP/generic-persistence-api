package app.softnetwork.specification

/**
  * Created by smanciot on 06/03/2020.
  */
sealed trait Specification[A] {

  private[this] var rules: List[Rule[A]] = List.empty

  protected def add(r: Rule[A]): Unit = {
    rules = rules.::(r)
  }

  def isSatisfiedBy(a: A): Boolean = {
    rules.forall(_.isSatisfiedBy(a))
  }

}

object Specification{
  def apply[A](rules: Rule[A]*): Specification[A] = {
    val specification = new Specification[A]{}
    rules.foreach(specification.add)
    specification
  }
}

trait Rule[A] {
  def isSatisfiedBy(a: A): Boolean
}
