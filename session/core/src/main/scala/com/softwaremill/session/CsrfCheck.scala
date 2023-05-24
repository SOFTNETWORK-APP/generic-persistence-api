package com.softwaremill.session

sealed trait CsrfCheck {
  def checkHeaderAndForm: Boolean
}

trait CsrfCheckHeader extends CsrfCheck {
  val checkHeaderAndForm: Boolean = false
}

trait CsrfCheckHeaderAndForm extends CsrfCheck {
  val checkHeaderAndForm: Boolean = true
}
