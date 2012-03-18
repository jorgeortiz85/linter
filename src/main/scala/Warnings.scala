package com.foursquare.lint

object Warnings extends Enumeration {
  type Warning = Value
  val JavaConversions,
  OptionGet,
  UnsafeContains,
  UnsafeEquals = Value
}
