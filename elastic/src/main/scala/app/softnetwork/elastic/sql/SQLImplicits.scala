package app.softnetwork.elastic.sql

import scala.util.matching.Regex

/**
  * Created by smanciot on 27/06/2018.
  */
object SQLImplicits {
  import scala.language.implicitConversions

  implicit def queryToSQLCriteria(query: String): Option[SQLCriteria] = {
    val sql: Option[SQLQuery] = query
    sql match {
      case Some(q) => q.where match {
        case Some(w) => w.criteria
        case _       => None
      }
      case _       => None
    }
  }
  implicit def queryToSQLQuery(query: String): Option[SQLQuery] = {
    SQLParser(query) match {
      case Left(l) => None
      case Right(r) => Some(r)
    }
  }

  implicit def sqllikeToRegex(value: String): Regex = toRegex(value).r

}
