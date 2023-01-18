package app.softnetwork.elastic.sql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Created by smanciot on 17/02/17.
  */
class SQLLiteralSpec extends AnyFlatSpec with Matchers {

  "SQLLiteral" should "perform sql like" in {
    val l = SQLLiteral("%dummy%")
    l.like(Seq("dummy")) should ===(true)
    l.like(Seq("aa dummy")) should ===(true)
    l.like(Seq("dummy bbb")) should ===(true)
    l.like(Seq("aaa dummy bbb")) should ===(true)
    l.like(Seq("dummY")) should ===(false)
  }
}
