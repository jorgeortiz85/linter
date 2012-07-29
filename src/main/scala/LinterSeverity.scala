package com.foursquare.lint

/**
 * Enumeration that models the different compile-time notifications Linter
 * can alert the developer to based on configuration of the different
 * types of lint checks.
 * 
 * These severities are a subset of the available notification functions on
 * the @scala.tools.nsc.CompilationUnits$CompilationUnit
 */
object LinterSeverity extends Enumeration {
  type LinterSeverity = Value
  val WARN = Value("warn")
  val ERROR = Value("error")
}
