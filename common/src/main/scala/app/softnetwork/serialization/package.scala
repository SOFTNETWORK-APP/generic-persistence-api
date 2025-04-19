package app.softnetwork

import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization
import org.json4s._

import java.text.SimpleDateFormat
import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.Date
import scala.language.implicitConversions
import scala.reflect.ClassTag

/** Created by smanciot on 14/05/2020.
  */
package object serialization {

  implicit val serialization: Serialization.type = jackson.Serialization

  val commonFormats: Formats =
    Serialization.formats(NoTypeHints) ++
    JodaTimeSerializers.all ++
    JavaTypesSerializers.all ++
    JavaTimeSerializers.all

  implicit def map2String(data: Map[String, Any])(implicit formats: Formats): String =
    serialization.write(data)

  val defaultExcludedFields: List[String] =
    List("serialVersionUID", "__serializedSizeCachedValue", "bitmap$0")

  def caseClass2Map(
    cc: AnyRef
  )(implicit excludedFields: List[String] = defaultExcludedFields): Map[String, Any] = {
    cc.getClass.getDeclaredFields
      .filterNot(f => excludedFields.contains(f.getName))
      .foldLeft(Map.empty[String, Any]) { (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> (f.get(cc) match {
          case o: Option[_] => o.orNull
          //case p: Product if p.productArity > 0 => caseClass2Map(p)
          case s => s
        }))
      }
  }

  def asJson(entity: AnyRef)(implicit formats: Formats = commonFormats): String = {
    implicit def excludedFields: List[String] = defaultExcludedFields :+ "data"
    def asMap: Map[String, Any] = caseClass2Map(entity)
    val data: String = asMap // implicit conversion Map[String, Any] => String
    data
  }

  /** required before migrating from kryo to another serialization format
    *
    * @param str
    *   - a string
    * @return
    *   an option
    */
  implicit def string2Option(str: String): Option[String] =
    if (str.trim.isEmpty) None else Some(str)

  /** required before migrating from kryo to another serialization format
    *
    * @param opt
    *   - an option
    * @return
    *   a string
    */
  implicit def option2String(opt: Option[String]): String = opt.getOrElse("")

  import scala.reflect.runtime.universe._
  import scala.reflect.runtime.{currentMirror => cm}

  def isCaseClass[T: TypeTag](paramType: Type): Boolean = {
    paramType.typeSymbol.isClass && paramType.typeSymbol.asClass.isCaseClass
  }

  def updateCaseClass[T: TypeTag](obj: T, updates: Map[String, Any])(implicit
    tag: ClassTag[T]
  ): T = {
    val classType = typeOf[T].typeSymbol.asClass
    val classMirror = cm.reflect(obj)

    // Get the primary constructor of the case class
    val constructor = typeOf[T].decl(termNames.CONSTRUCTOR).asMethod
    val classInstanceMirror = cm.reflectClass(classType)

    // Get the parameters of the constructor
    val params = constructor.paramLists.flatten

    // Build new parameter values (updated if in the map, or original if not)
    val newArgs = params.map { param =>
      val paramName = param.name.toString
      val paramType = param.typeSignature
      updates.get(paramName) match {
        case Some(newValue) =>
          // Handle type conversion based on the expected parameter type
          (paramType, newValue) match {
            case (t, v: String) if t =:= typeOf[Boolean] =>
              v.toBoolean // Convert string to Boolean
            case (t, v: String) if t =:= typeOf[Date] =>
              new SimpleDateFormat().parse(v) // Convert string to Date
            case (t, v: String) if t =:= typeOf[Double] =>
              v.toDouble // Convert string to Double
            case (t, v: String) if t =:= typeOf[Float] =>
              v.toFloat // Convert string to Float
            case (t, v: String) if t =:= typeOf[Instant] =>
              Instant.parse(v) // Convert string to Instant
            case (t, v: String) if t =:= typeOf[Int] =>
              v.toInt // Convert string to Int
            case (t, v: String) if t =:= typeOf[LocalDate] =>
              LocalDate.parse(v) // Convert string to LocalDate
            case (t, v: String) if t =:= typeOf[LocalDateTime] =>
              LocalDateTime.parse(v) // Convert string to LocalDateTime
            case (t, v: String) if t =:= typeOf[Long] =>
              v.toLong // Convert string to Long
            case (t, v: String) if t =:= typeOf[Short] =>
              v.toShort // Convert string to Short
            case (t, v: Map[String, Any]) if isCaseClass(t) =>
              updateCaseClass(classMirror.reflectField(t.decl(TermName(paramName)).asTerm).get, v)
            case _ =>
              newValue // If types match or are already compatible, use the value directly
          }
        case None =>
          // Reflectively get the current field value from the case class instance
          val fieldTerm = typeOf[T].decl(TermName(paramName)).asTerm
          classMirror.reflectField(fieldTerm).get
      }
    }

    // Create a new instance of the case class using the updated arguments
    classInstanceMirror.reflectConstructor(constructor)(newArgs: _*).asInstanceOf[T]
  }
}
