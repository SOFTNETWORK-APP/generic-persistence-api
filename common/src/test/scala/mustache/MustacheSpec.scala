package mustache

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import mustache._

import scala.io.Source

/**
  * Created by smanciot on 08/04/2018.
  */
class MustacheSpec extends AnyWordSpec with Matchers {

  "Mustache" must {
    "render template propertly" in {
      Mustache("template/hello.mustache").render(Map("name"->"world")) shouldBe "Hello world !"
    }
  }
}
