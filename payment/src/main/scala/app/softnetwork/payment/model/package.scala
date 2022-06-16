package app.softnetwork.payment

package object model {

  /**
    * @param externalUuid - external unique id
    * @param profile - optional profile
    * @return external unique id with profile to lowercase
    */
  def computeExternalUuidWithProfile(externalUuid: String, profile: Option[String]): String = {
    profile match {
      case Some(p) => externalUuid + s"#${p.toLowerCase}"
      case _ => externalUuid
    }
  }

}
