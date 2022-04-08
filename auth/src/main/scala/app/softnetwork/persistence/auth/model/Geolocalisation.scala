package app.softnetwork.persistence.auth.model

/**
  * Created by smanciot on 26/03/2018.
  */
object Geolocalisation {

  /**
    * Calculate distance between two points in latitude and longitude taking
    * into account height difference. If you are not interested in height
    * difference pass 0.0. Uses Haversine method as its base.
    *
    * @return Distance in Meters
    */
  def distance(from: Poi, to: Poi): Double = {

    val R: Int = 6371 // Radius of the earth

    val latDistance = Math.toRadians(to.latitude - from.latitude)

    val lonDistance = Math.toRadians(to.longitude - from.longitude)

    val a = Math.sin(latDistance / 2) *
      Math.sin(latDistance / 2) +
      Math.cos(Math.toRadians(from.latitude)) *
        Math.cos(Math.toRadians(to.latitude)) *
        Math.sin(lonDistance / 2) *
        Math.sin(lonDistance / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    var distance = R * c * 1000 // convert to meters

    val height = from.endAltitude - to.endAltitude

    distance = Math.pow(distance, 2) + Math.pow(height, 2)

    Math.sqrt(distance)
  }

  def isEligible(from: Poi, to: Poi, maxDistance: Double): Boolean = {
    distance(from, to) <= maxDistance
  }
}
