package app.softnetwork

import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization
import org.json4s._

import scala.language.implicitConversions

/**
  * Created by smanciot on 14/05/2020.
  */
package object serialization {

  implicit val serialization = jackson.Serialization

  val commonFormats =
    Serialization.formats(NoTypeHints) ++
      JodaTimeSerializers.all ++
      JavaTypesSerializers.all ++
      JavaTimeSerializers.all


  implicit def map2String(data: Map[String, Any])(implicit formats: Formats): String = serialization.write(data)

  val defaultExcludedFields = List("serialVersionUID", "__serializedSizeCachedValue", "bitmap$0")

  def caseClass2Map(cc: AnyRef)(implicit excludedFields: List[String] = defaultExcludedFields): Map[String, Any] = {
    cc.getClass.getDeclaredFields.filterNot(f => excludedFields.contains(f.getName)).foldLeft(Map.empty[String, Any]) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> (f.get(cc) match {
        case o: Option[_] => o.orNull
        //case p: Product if p.productArity > 0 => caseClass2Map(p)
        case s => s
      }))
    }
  }

  def asJson(entity: AnyRef)(implicit formats: Formats = commonFormats): String = {
    implicit def excludedFields = defaultExcludedFields:+ "data"
    def asMap: Map[String, Any] = caseClass2Map(entity)
    val data: String = asMap // implicit conversion Map[String, Any] => String
    data
  }

  /**
    * required before migrating from kryo to another serialization format
    *
    * @param str - a string
    * @return an option
    */
  implicit def string2Option(str: String): Option[String] = if (str.trim.isEmpty) None else Some(str)

  /**
    * required before migrating from kryo to another serialization format
    *
    * @param opt - an option
    * @return a string
    */
  implicit def option2String(opt: Option[String]): String = opt.getOrElse("")

}
