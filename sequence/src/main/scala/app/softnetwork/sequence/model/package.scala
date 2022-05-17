package app.softnetwork.sequence

package object model {
  trait GenericSequence {
    def name: String
    def value: Int
    val uuid: String = name
  }
}
